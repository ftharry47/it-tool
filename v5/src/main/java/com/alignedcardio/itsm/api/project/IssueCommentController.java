package com.alignedcardio.itsm.api.project;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.IssueCommentService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/issues/{issueId}/comments")
public class IssueCommentController {

    private final IssueCommentService issueCommentService;
    private final UserService userService;

    public IssueCommentController(IssueCommentService issueCommentService, UserService userService) {
        this.issueCommentService = issueCommentService;
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<IssueCommentResponse> list(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID issueId) {
        AppUser user = userService.syncFromJwt(jwt);
        return issueCommentService.list(user.getOrgId(), issueId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public IssueCommentResponse create(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID issueId,
                                       @Valid @RequestBody IssueCommentCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return issueCommentService.create(user.getOrgId(), user.getId(), issueId, request);
    }
}
