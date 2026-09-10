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
public class IncidentController {

    private final UserService userService;
    private final IncidentService incidentService;

    public IncidentController(UserService userService, IncidentService incidentService) {
        this.userService = userService;
        this.incidentService = incidentService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public List<IncidentSummary> list(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false, defaultValue = "false") boolean showDeleted) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.list(user.getOrgId(), showDeleted);
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public List<IncidentSummary> listMy(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.listByReporter(user.getOrgId(), user.getId());
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IncidentResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody IncidentCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentService.create(user, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public IncidentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.get(user.getOrgId(), id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public IncidentResponse update(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody IncidentUpdateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.update(user, user.getOrgId(), id, request);
    }

    @GetMapping("/priorities")
    @PreAuthorize("isAuthenticated()")
    public List<PriorityOption> priorities(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.priorities(user.getOrgId());
    }

    @GetMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public List<CategoryOption> categories(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return incidentService.categories(user.getOrgId());
    }
}
