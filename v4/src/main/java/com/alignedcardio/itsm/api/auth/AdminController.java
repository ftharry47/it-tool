package com.alignedcardio.itsm.api.auth;

import com.alignedcardio.itsm.entity.AppUser;
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
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public List<CurrentUser> listUsers() {
        return userService.findAllByOrgIdDefault();
    }

    @PatchMapping("/users/{id}/role")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public CurrentUser updateRole(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody UserUpdateRoleRequest request,
                                  HttpServletRequest httpRequest) {
        AppUser actor = userService.syncFromJwt(jwt);
        return userService.updateRole(id, request.role(), actor.getId(), httpRequest.getRemoteAddr());
    }

    @PatchMapping("/users/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public CurrentUser updateUser(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody UpdateUserRequest request) {
        AppUser actor = userService.syncFromJwt(jwt);
        return userService.updateUser(id, request, actor.getId());
    }
}
