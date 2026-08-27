package com.alignedcardio.itsm.api.project;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.UserService;
import com.alignedcardio.itsm.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final UserService userService;

    public WorkflowController(WorkflowService workflowService, UserService userService) {
        this.workflowService = workflowService;
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<WorkflowResponse> list(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return workflowService.list(user.getOrgId());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public WorkflowResponse create(@AuthenticationPrincipal Jwt jwt,
                                   @Valid @RequestBody WorkflowCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return workflowService.create(user.getOrgId(), user.getId(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public WorkflowResponse get(@AuthenticationPrincipal Jwt jwt,
                                @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return workflowService.get(user.getOrgId(), id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public WorkflowResponse update(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody WorkflowCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return workflowService.update(user.getOrgId(), user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public void delete(@AuthenticationPrincipal Jwt jwt,
                       @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        workflowService.delete(user.getOrgId(), user.getId(), id);
    }

    @PostMapping("/{id}/statuses")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public WorkflowStatusResponse addStatus(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID id,
                                            @Valid @RequestBody WorkflowStatusCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return workflowService.addStatus(user.getOrgId(), user.getId(), id, request);
    }

    @DeleteMapping("/{id}/statuses/{statusId}")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public void removeStatus(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable UUID id,
                             @PathVariable UUID statusId) {
        AppUser user = userService.syncFromJwt(jwt);
        workflowService.removeStatus(user.getOrgId(), id, statusId);
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public WorkflowTransitionResponse addTransition(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID id,
                                                    @Valid @RequestBody WorkflowTransitionCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return workflowService.addTransition(user.getOrgId(), user.getId(), id, request);
    }

    @DeleteMapping("/{id}/transitions/{transitionId}")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public void removeTransition(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable UUID id,
                                 @PathVariable UUID transitionId) {
        AppUser user = userService.syncFromJwt(jwt);
        workflowService.removeTransition(user.getOrgId(), id, transitionId);
    }
}
