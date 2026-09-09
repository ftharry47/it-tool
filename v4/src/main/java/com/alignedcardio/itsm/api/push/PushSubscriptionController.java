package com.alignedcardio.itsm.api.push;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.PushSubscription;
import com.alignedcardio.itsm.repository.PushSubscriptionRepository;
import com.alignedcardio.itsm.service.UserService;
import com.alignedcardio.itsm.service.notification.PushService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/push")
public class PushSubscriptionController {

    private final PushSubscriptionRepository subscriptionRepository;
    private final UserService userService;
    private final PushService pushService;

    public PushSubscriptionController(PushSubscriptionRepository subscriptionRepository,
                                      UserService userService,
                                      PushService pushService) {
        this.subscriptionRepository = subscriptionRepository;
        this.userService = userService;
        this.pushService = pushService;
    }

    public record SubscriptionRequest(
            @NotBlank String endpoint,
            @NotBlank String p256dh,
            @NotBlank String auth,
            String userAgent
    ) {}

    public record SubscriptionResponse(UUID id, String endpoint, String userAgent) {}

    @GetMapping("/vapid-public-key")
    @PreAuthorize("isAuthenticated()")
    public Map<String, String> vapidPublicKey() {
        return Map.of("publicKey", pushService.getPublicKey() != null ? pushService.getPublicKey() : "");
    }

    @GetMapping("/subscriptions")
    @PreAuthorize("isAuthenticated()")
    public List<SubscriptionResponse> list(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.syncFromJwt(jwt);
        return subscriptionRepository.findByUserIdAndDeletedAtIsNull(user.getId()).stream()
                .map(s -> new SubscriptionResponse(s.getId(), s.getEndpoint(), s.getUserAgent()))
                .toList();
    }

    @PostMapping("/subscriptions")
    @PreAuthorize("isAuthenticated()")
    public SubscriptionResponse subscribe(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody SubscriptionRequest request) {
        AppUser user = userService.syncFromJwt(jwt);

        PushSubscription sub = subscriptionRepository
                .findByUserIdAndEndpointAndDeletedAtIsNull(user.getId(), request.endpoint())
                .orElseGet(PushSubscription::new);

        sub.setOrgId(user.getOrgId());
        sub.setUserId(user.getId());
        sub.setEndpoint(request.endpoint());
        sub.setP256dh(request.p256dh());
        sub.setAuth(request.auth());
        sub.setUserAgent(request.userAgent());
        sub = subscriptionRepository.save(sub);

        return new SubscriptionResponse(sub.getId(), sub.getEndpoint(), sub.getUserAgent());
    }

    @DeleteMapping("/subscriptions/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID id) {
        AppUser user = userService.syncFromJwt(jwt);
        subscriptionRepository.findByIdAndUserIdAndDeletedAtIsNull(id, user.getId())
                .ifPresent(sub -> {
                    sub.softDelete();
                    subscriptionRepository.save(sub);
                });
        return ResponseEntity.noContent().build();
    }
}
