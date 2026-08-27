package com.alignedcardio.itsm.api.project;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Project;
import com.alignedcardio.itsm.repository.ProjectRepository;
import com.alignedcardio.itsm.service.IssueService;
import com.alignedcardio.itsm.service.ProjectService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final UserService userService;
    private final IssueService issueService;
    private final ProjectRepository projectRepository;

    public ProjectController(ProjectService projectService,
                             UserService userService,
                             IssueService issueService,
                             ProjectRepository projectRepository) {
        this.projectService = projectService;
        this.userService = userService;
        this.issueService = issueService;
        this.projectRepository = projectRepository;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ProjectResponse> list(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return projectService.list(user.getOrgId());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ProjectResponse create(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody ProjectCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return projectService.create(user.getOrgId(), user.getId(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ProjectResponse get(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return projectService.get(user.getOrgId(), id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public ProjectResponse update(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody ProjectUpdateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return projectService.update(user.getOrgId(), user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public void delete(@AuthenticationPrincipal Jwt jwt,
                       @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        projectService.delete(user.getOrgId(), user.getId(), id);
    }

    @GetMapping("/{key}/board")
    @PreAuthorize("isAuthenticated()")
    public List<BoardColumn> board(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable String key,
                                   @RequestParam UUID workflowId,
                                   @RequestParam(required = false) UUID sprintId) {
        AppUser user = userService.syncFromJwt(jwt);
        Project project = projectRepository.findByOrgIdAndKeyAndDeletedAtIsNull(user.getOrgId(), key.toUpperCase())
                .orElseThrow(() -> new com.alignedcardio.itsm.service.NotFoundException("Project not found"));
        return issueService.boardByProject(user.getOrgId(), project.getId(), workflowId, sprintId);
    }
}
