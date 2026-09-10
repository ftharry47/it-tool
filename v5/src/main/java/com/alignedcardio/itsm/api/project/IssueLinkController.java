package com.alignedcardio.itsm.api.project;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.IssueLinkService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/issues/{issueId}/links")
public class IssueLinkController {

    private final IssueLinkService issueLinkService;
    private final UserService userService;

    public IssueLinkController(IssueLinkService issueLinkService, UserService userService) {
        this.issueLinkService = issueLinkService;
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<IssueLinkResponse> list(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable UUID issueId) {
        AppUser user = userService.syncFromJwt(jwt);
        return issueLinkService.list(user.getOrgId(), issueId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public IssueLinkResponse create(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID issueId,
                                    @Valid @RequestBody IssueLinkCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return issueLinkService.create(user.getOrgId(), user.getId(), issueId, request);
    }

    @DeleteMapping("/{linkId}")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ADMIN','SUPER_ADMIN')")
    public void delete(@AuthenticationPrincipal Jwt jwt,
                       @PathVariable UUID issueId,
                       @PathVariable UUID linkId) {
        AppUser user = userService.syncFromJwt(jwt);
        issueLinkService.delete(user.getOrgId(), linkId);
    }
}
