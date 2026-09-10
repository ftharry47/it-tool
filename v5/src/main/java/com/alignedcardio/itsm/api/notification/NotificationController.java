package com.alignedcardio.itsm.api.notification;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.service.UserService;
import com.alignedcardio.itsm.service.notification.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam(name = "unreadOnly", required = false, defaultValue = "false") boolean unreadOnly,
                                          @RequestParam(name = "limit", required = false) Integer limit) {
        AppUser user = userService.syncFromJwt(jwt);
        return notificationService.findByUser(user.getId(), unreadOnly, limit)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/unread-count")
    public long unreadCount(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return notificationService.countUnreadByUser(user.getId());
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        notificationService.markRead(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        notificationService.markAllRead(user.getId());
        return ResponseEntity.ok().build();
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getSubject(),
                n.getBody(),
                n.getEntityType(),
                n.getEntityId(),
                n.getChannel() == null ? null : n.getChannel().name(),
                n.getReadAt() != null,
                n.getReadAt(),
                n.getCreatedAt());
    }
}
