package com.alignedcardio.itsm.api.user;

import com.alignedcardio.itsm.api.auth.CurrentUser;
import com.alignedcardio.itsm.api.auth.UpdateUserRequest;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.GraphUserSyncService;
import com.alignedcardio.itsm.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final GraphUserSyncService graphUserSyncService;

    public UserController(UserService userService, GraphUserSyncService graphUserSyncService) {
        this.userService = userService;
        this.graphUserSyncService = graphUserSyncService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<UserResponse> list(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return userService.listByOrg(user.getOrgId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public UserResponse get(@AuthenticationPrincipal Jwt jwt,
                            @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        return userService.getByOrgAndId(user.getOrgId(), id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public UserResponse create(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody UserCreateRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return userService.createLocalUser(user.getOrgId(), request);
    }

    @PostMapping("/sync-from-ad")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public AdSyncResult syncFromAd(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return graphUserSyncService.sync(user.getOrgId());
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public CurrentUser updateRole(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody UserRoleUpdateRequest request,
                                  HttpServletRequest httpRequest) {
        AppUser user = userService.syncFromJwt(jwt);
        return userService.updateRole(id, request.roleName(), user.getId(), httpRequest.getRemoteAddr());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public CurrentUser updateUser(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody UpdateUserRequest request) {
        AppUser user = userService.syncFromJwt(jwt);
        return userService.updateUser(id, request, user.getId());
    }
}
