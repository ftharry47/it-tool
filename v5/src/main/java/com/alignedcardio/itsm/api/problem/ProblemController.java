package com.alignedcardio.itsm.api.problem;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Problem;
import com.alignedcardio.itsm.service.ProblemService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/problems")
public class ProblemController {

    private final UserService userService;
    private final ProblemService problemService;

    public ProblemController(UserService userService, ProblemService problemService) {
        this.userService = userService;
        this.problemService = problemService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public List<ProblemResponse> list(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(defaultValue = "false") boolean mine,
                                      @RequestParam(required = false, defaultValue = "false") boolean showDeleted) {
        AppUser user = userService.syncFromJwt(jwt);
        return problemService.list(user.getOrgId(), mine ? user.getId() : null, showDeleted);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ProblemResponse create(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody ProblemCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return problemService.create(user, user.getOrgId(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ProblemResponse get(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return problemService.get(user.getOrgId(), id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ProblemResponse update(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody ProblemUpdateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return problemService.update(user, user.getOrgId(), id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ProblemResponse updateStatus(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable UUID id,
                                        @Valid @RequestBody ProblemStatusUpdateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return problemService.updateStatus(user, user.getOrgId(), id, Problem.Status.valueOf(request.status()));
    }

    @PostMapping("/{id}/link-incident")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ProblemResponse linkIncident(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable UUID id,
                                        @Valid @RequestBody LinkIncidentRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return problemService.linkIncident(user, user.getOrgId(), id, request);
    }

    @GetMapping("/{id}/incidents")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public List<ProblemService.IncidentSummary> listIncidents(@AuthenticationPrincipal Jwt jwt,
                                                             @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return problemService.listLinkedIncidents(user.getOrgId(), id);
    }

    @GetMapping("/{id}/activity")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public List<com.alignedcardio.itsm.api.auth.AuditLogResponse> listActivity(@AuthenticationPrincipal Jwt jwt,
                                                                                @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return problemService.listActivity(user.getOrgId(), id);
    }
}
