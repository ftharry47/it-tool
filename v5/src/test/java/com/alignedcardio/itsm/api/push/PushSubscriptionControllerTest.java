package com.alignedcardio.itsm.api.push;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.PushSubscription;
import com.alignedcardio.itsm.repository.PushSubscriptionRepository;
import com.alignedcardio.itsm.service.UserService;
import com.alignedcardio.itsm.service.notification.PushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionControllerTest {

    @Mock
    private PushSubscriptionRepository subscriptionRepository;

    @Mock
    private UserService userService;

    @Mock
    private PushService pushService;

    @Mock
    private Jwt jwt;

    private PushSubscriptionController controller;

    private AppUser user;

    @BeforeEach
    void setUp() {
        controller = new PushSubscriptionController(subscriptionRepository, userService, pushService);
        user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setOrgId(UUID.randomUUID());
        lenient().when(userService.syncFromJwt(jwt)).thenReturn(user);
    }

    @Test
    void subscribeCreatesNewSubscription() {
        var request = new PushSubscriptionController.SubscriptionRequest(
                "https://push.example.com/ep1", "p256dh-key", "auth-secret", "Chrome");

        when(subscriptionRepository.findByUserIdAndEndpointAndDeletedAtIsNull(user.getId(), request.endpoint()))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(PushSubscription.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = controller.subscribe(jwt, request);

        assertEquals(request.endpoint(), response.endpoint());
        verify(subscriptionRepository).save(argThat(s ->
                s.getUserId().equals(user.getId())
                        && s.getOrgId().equals(user.getOrgId())
                        && "p256dh-key".equals(s.getP256dh())
                        && "auth-secret".equals(s.getAuth())));
    }

    @Test
    void subscribeUpsertsExistingEndpoint() {
        PushSubscription existing = new PushSubscription();
        existing.setUserId(user.getId());
        existing.setEndpoint("https://push.example.com/ep1");
        existing.setP256dh("old-key");
        existing.setAuth("old-auth");

        var request = new PushSubscriptionController.SubscriptionRequest(
                "https://push.example.com/ep1", "new-key", "new-auth", "Edge");

        when(subscriptionRepository.findByUserIdAndEndpointAndDeletedAtIsNull(user.getId(), request.endpoint()))
                .thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any(PushSubscription.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = controller.subscribe(jwt, request);

        assertEquals("new-key", existing.getP256dh());
        assertEquals("new-auth", existing.getAuth());
        assertEquals("Edge", existing.getUserAgent());
        verify(subscriptionRepository).save(existing);
    }

    @Test
    void unsubscribeSoftDeletesOwnSubscription() {
        PushSubscription sub = new PushSubscription();
        sub.setUserId(user.getId());
        UUID subId = UUID.randomUUID();

        when(subscriptionRepository.findByIdAndUserIdAndDeletedAtIsNull(subId, user.getId()))
                .thenReturn(Optional.of(sub));

        var response = controller.unsubscribe(jwt, subId);

        assertEquals(204, response.getStatusCode().value());
        assertNotNull(sub.getDeletedAt());
        verify(subscriptionRepository).save(sub);
    }

    @Test
    void unsubscribeIgnoresOtherUsersSubscription() {
        UUID subId = UUID.randomUUID();
        when(subscriptionRepository.findByIdAndUserIdAndDeletedAtIsNull(subId, user.getId()))
                .thenReturn(Optional.empty());

        var response = controller.unsubscribe(jwt, subId);

        assertEquals(204, response.getStatusCode().value());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void listReturnsOnlyActiveSubscriptions() {
        PushSubscription sub = new PushSubscription();
        sub.setUserId(user.getId());
        sub.setEndpoint("https://push.example.com/ep1");
        sub.setUserAgent("Chrome");

        when(subscriptionRepository.findByUserIdAndDeletedAtIsNull(user.getId()))
                .thenReturn(List.of(sub));

        var result = controller.list(jwt);

        assertEquals(1, result.size());
        assertEquals("https://push.example.com/ep1", result.get(0).endpoint());
    }

    @Test
    void vapidPublicKeyReturnsConfiguredValue() {
        when(pushService.getPublicKey()).thenReturn("BP_test_key");
        assertEquals("BP_test_key", controller.vapidPublicKey().get("publicKey"));
    }
}
