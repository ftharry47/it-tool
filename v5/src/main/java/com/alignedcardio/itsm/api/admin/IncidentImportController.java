package com.alignedcardio.itsm.api.admin;

import com.alignedcardio.itsm.service.IncidentImportService;
import com.alignedcardio.itsm.service.IncidentImportService.ImportOverrides;
import com.alignedcardio.itsm.service.IncidentImportService.ImportPreview;
import com.alignedcardio.itsm.service.IncidentImportService.ImportResult;
import com.alignedcardio.itsm.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/import")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class IncidentImportController {

    private final UserService userService;
    private final IncidentImportService importService;
    private final ObjectMapper objectMapper;

    public IncidentImportController(UserService userService, IncidentImportService importService,
                                    ObjectMapper objectMapper) {
        this.userService = userService;
        this.importService = importService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/incidents/preview")
    public ResponseEntity<?> preview(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "overrides", required = false) String overridesJson)
            throws IOException {
        var user = userService.syncFromJwt(jwt);
        try {
            ImportPreview result = importService.preview(file.getInputStream(), user.getOrgId(),
                    parseOverrides(overridesJson));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/incidents/commit")
    public ResponseEntity<?> commit(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "overrides", required = false) String overridesJson)
            throws IOException {
        var user = userService.syncFromJwt(jwt);
        try {
            ImportResult result = importService.commit(file.getInputStream(), user.getOrgId(), user,
                    parseOverrides(overridesJson));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** JSON shape: {"location": {"raw name": "<uuid>"}, "assignee": {"raw name": "<uuid>"}} */
    private ImportOverrides parseOverrides(String json) {
        if (json == null || json.isBlank()) return ImportOverrides.empty();
        try {
            Map<?, ?> root = objectMapper.readValue(json, Map.class);
            return new ImportOverrides(
                    toUuidMap(root.get("location")),
                    toUuidMap(root.get("assignee")));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid overrides JSON: " + e.getMessage());
        }
    }

    private Map<String, UUID> toUuidMap(Object node) {
        if (!(node instanceof Map<?, ?> m)) return Map.of();
        Map<String, UUID> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            try {
                out.put(String.valueOf(e.getKey()), UUID.fromString(String.valueOf(e.getValue())));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }
}
