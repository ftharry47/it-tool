package com.alignedcardio.itsm.api.project;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.IssueTypeService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/issue-types")
public class IssueTypeController {

    private final IssueTypeService issueTypeService;
    private final UserService userService;

    public IssueTypeController(IssueTypeService issueTypeService, UserService userService) {
        this.issueTypeService = issueTypeService;
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<IssueTypeResponse> list(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return issueTypeService.list(user.getOrgId());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public IssueTypeResponse create(@AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody IssueTypeCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return issueTypeService.create(user.getOrgId(), user.getId(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public IssueTypeResponse get(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return issueTypeService.get(user.getOrgId(), id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public IssueTypeResponse update(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody IssueTypeCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return issueTypeService.update(user.getOrgId(), user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public void delete(@AuthenticationPrincipal Jwt jwt,
                       @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        issueTypeService.delete(user.getOrgId(), user.getId(), id);
    }
}
