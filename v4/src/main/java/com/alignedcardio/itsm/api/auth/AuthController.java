package com.alignedcardio.itsm.api.auth;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUser> me(@AuthenticationPrincipal Jwt jwt) {
        try {
            if (jwt == null) {
                logger.warn("[AuthController] /api/auth/me: JWT is null");
                return ResponseEntity.status(401).build();
            }

            logger.info("[AuthController] /api/auth/me: Processing JWT for subject: {}", jwt.getSubject());
            AppUser user = userService.syncFromJwt(jwt);
            logger.info("[AuthController] /api/auth/me: User synced: {}", user.getEmail());

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
        } catch (Exception e) {
            logger.error("[AuthController] /api/auth/me: Error", e);
            return ResponseEntity.status(500).build();
        }
    }
}
