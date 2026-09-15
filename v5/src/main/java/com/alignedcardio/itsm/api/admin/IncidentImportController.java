package com.alignedcardio.itsm.api.admin;

import com.alignedcardio.itsm.service.IncidentImportService;
import com.alignedcardio.itsm.service.IncidentImportService.ImportPreview;
import com.alignedcardio.itsm.service.IncidentImportService.ImportResult;
import com.alignedcardio.itsm.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/import")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class IncidentImportController {

    private final UserService userService;
    private final IncidentImportService importService;

    public IncidentImportController(UserService userService, IncidentImportService importService) {
        this.userService = userService;
        this.importService = importService;
    }

    @PostMapping("/incidents/preview")
    public ResponseEntity<?> preview(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam("file") MultipartFile file) throws IOException {
        var user = userService.syncFromJwt(jwt);
        try {
            ImportPreview result = importService.preview(file.getInputStream(), user.getOrgId());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/incidents/commit")
    public ResponseEntity<?> commit(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam("file") MultipartFile file) throws IOException {
        var user = userService.syncFromJwt(jwt);
        try {
            ImportResult result = importService.commit(file.getInputStream(), user.getOrgId(), user);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
