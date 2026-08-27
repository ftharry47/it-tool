package com.alignedcardio.itsm.api.project;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.IssueService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/issues")
public class IssueController {

    private final IssueService issueService;
    private final UserService userService;

    public IssueController(IssueService issueService, UserService userService) {
        this.issueService = issueService;
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public IssueResponse create(@AuthenticationPrincipal Jwt jwt,
                                @Valid @RequestBody IssueCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return issueService.create(user.getOrgId(), user.getId(), request);
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public List<IssueResponse> listByProject(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID projectId) {
        AppUser user = userService.syncFromJwt(jwt);
        return issueService.listByProject(user.getOrgId(), projectId);
    }

    @GetMapping("/project/{projectId}/backlog")
    @PreAuthorize("isAuthenticated()")
    public List<IssueResponse> backlog(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID projectId) {
        AppUser user = userService.syncFromJwt(jwt);
        return issueService.backlog(user.getOrgId(), projectId);
    }

    @GetMapping("/sprint/{sprintId}")
    @PreAuthorize("isAuthenticated()")
    public List<IssueResponse> listBySprint(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID sprintId) {
        AppUser user = userService.syncFromJwt(jwt);
        return issueService.listBySprint(user.getOrgId(), sprintId);
    }

    @GetMapping("/sprint/{sprintId}/board")
    @PreAuthorize("isAuthenticated()")
    public List<BoardColumn> board(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID sprintId) {
        AppUser user = userService.syncFromJwt(jwt);
        return issueService.board(user.getOrgId(), sprintId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public IssueResponse get(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return issueService.get(user.getOrgId(), id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public IssueResponse update(@AuthenticationPrincipal Jwt jwt,
                                @PathVariable UUID id,
                                @Valid @RequestBody IssueCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return issueService.update(user.getOrgId(), user.getId(), id, request);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public IssueResponse changeStatus(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody IssueStatusChangeRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return issueService.changeStatus(user.getOrgId(), user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public void delete(@AuthenticationPrincipal Jwt jwt,
                       @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        issueService.delete(user.getOrgId(), user.getId(), id);
    }
}
