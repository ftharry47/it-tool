package com.alignedcardio.itsm.api.project;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.BurndownService;
import com.alignedcardio.itsm.service.SprintService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/sprints")
public class SprintController {

    private final SprintService sprintService;
    private final BurndownService burndownService;
    private final UserService userService;

    public SprintController(SprintService sprintService,
                            BurndownService burndownService,
                            UserService userService) {
        this.sprintService = sprintService;
        this.burndownService = burndownService;
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<SprintResponse> list(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable UUID projectId) {
        AppUser user = userService.syncFromJwt(jwt);
        return sprintService.listByProject(user.getOrgId(), projectId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public SprintResponse create(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable UUID projectId,
                                 @Valid @RequestBody SprintCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return sprintService.create(user.getOrgId(), user.getId(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public SprintResponse get(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID projectId,
                              @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return sprintService.get(user.getOrgId(), projectId, id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public SprintResponse update(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable UUID projectId,
                                 @PathVariable UUID id,
                                 @Valid @RequestBody SprintCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return sprintService.update(user.getOrgId(), user.getId(), id, request);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public SprintResponse start(@AuthenticationPrincipal Jwt jwt,
                                @PathVariable UUID projectId,
                                @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return sprintService.start(user.getOrgId(), user.getId(), id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public SprintResponse complete(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID projectId,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody SprintCompleteRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return sprintService.complete(user.getOrgId(), user.getId(), id, request);
    }

    @GetMapping("/{id}/burndown")
    @PreAuthorize("isAuthenticated()")
    public List<BurndownSnapshotResponse> burndown(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable UUID projectId,
                                                   @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return burndownService.getBurndown(user.getOrgId(), id);
    }
}
