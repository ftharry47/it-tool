package com.alignedcardio.itsm.api.auth;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUser> me(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(401).build();
        }

        AppUser user = userService.syncFromJwt(jwt);

        List<String> roles = user.getUserRoles().stream()
                .map(ur -> ur.getRole().getName())
                .toList();

        CurrentUser currentUser = new CurrentUser(
                user.getId(),
                user.getObjectId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getJobTitle(),
                user.getDepartment(),
                roles,
                user.isActive(),
                user.isMfaEnabled(),
                user.getManagerId()
        );

        return ResponseEntity.ok(currentUser);
    }
}
