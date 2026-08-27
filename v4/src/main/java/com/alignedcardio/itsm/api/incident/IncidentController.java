package com.alignedcardio.itsm.api.incident;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.IncidentService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/incidents")
@PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
public class IncidentController {

    private final UserService userService;
    private final IncidentService incidentService;

    public IncidentController(UserService userService, IncidentService incidentService) {
        this.userService = userService;
        this.incidentService = incidentService;
    }

    @GetMapping
    public List<IncidentSummary> list(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.list(user.getOrgId());
    }

    @PostMapping
    public ResponseEntity<IncidentResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody IncidentCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentService.create(user, request));
    }

    @GetMapping("/{id}")
    public IncidentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.get(user.getOrgId(), id);
    }

    @PatchMapping("/{id}")
    public IncidentResponse update(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody IncidentUpdateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.update(user, user.getOrgId(), id, request);
    }

    @GetMapping("/priorities")
    public List<PriorityOption> priorities(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.priorities(user.getOrgId());
    }

    @GetMapping("/categories")
    public List<CategoryOption> categories(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.categories(user.getOrgId());
    }
}
