package com.alignedcardio.itsm.service.notification;

import com.alignedcardio.itsm.entity.PushSubscription;
import com.alignedcardio.itsm.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushServiceTest {

    @Mock
    private PushSubscriptionRepository subscriptionRepository;

    private PushService pushService;

    /** Test double: overrides the HTTP seam and the enabled check. */
    private static class StubPushService extends PushService {
        private final Deque<Object> scripted = new ArrayDeque<>();

        StubPushService(PushSubscriptionRepository repo) {
            super(repo);
        }

        void scriptStatus(int status) { scripted.add(status); }
        void scriptError(Exception e) { scripted.add(e); }

        @Override
        public boolean isEnabled() { return true; }

        @Override
        int deliver(PushSubscription sub, String payload) throws Exception {
            Object next = scripted.poll();
            if (next instanceof Exception e) throw e;
            return (Integer) next;
        }
    }

    private StubPushService stub;

    @BeforeEach
    void setUp() {
        pushService = new PushService(subscriptionRepository);
        stub = new StubPushService(subscriptionRepository);
    }

    private PushSubscription sub(UUID userId, String endpoint) {
        PushSubscription s = new PushSubscription();
        s.setUserId(userId);
        s.setEndpoint(endpoint);
        s.setP256dh("key");
        s.setAuth("auth");
        return s;
    }

    @Test
    void disabledWhenVapidKeysMissing() {
        ReflectionTestUtils.setField(pushService, "vapidPublicKey", "");
        ReflectionTestUtils.setField(pushService, "vapidPrivateKey", "");
        pushService.init();

        assertFalse(pushService.isEnabled());
        assertFalse(pushService.sendToUser(UUID.randomUUID(), "t", "b", "/"));
        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void noSubscriptionsReturnsFalse() {
        UUID userId = UUID.randomUUID();
        when(subscriptionRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(List.of());

        assertFalse(stub.sendToUser(userId, "t", "b", "/"));
    }

    @Test
    void deadSubscriptionSoftDeletedOn410() {
        UUID userId = UUID.randomUUID();
        PushSubscription dead = sub(userId, "https://push.example.com/dead");

        when(subscriptionRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(List.of(dead));
        stub.scriptStatus(410);

        assertFalse(stub.sendToUser(userId, "t", "b", "/"));
        assertNotNull(dead.getDeletedAt());
        verify(subscriptionRepository).save(dead);
    }

    @Test
    void deadSubscriptionDoesNotBlockHealthyOne() {
        UUID userId = UUID.randomUUID();
        PushSubscription dead = sub(userId, "https://push.example.com/dead");
        PushSubscription alive = sub(userId, "https://push.example.com/alive");

        when(subscriptionRepository.findByUserIdAndDeletedAtIsNull(userId))
                .thenReturn(List.of(dead, alive));
        stub.scriptStatus(410);
        stub.scriptStatus(201);

        assertTrue(stub.sendToUser(userId, "t", "b", "/"));
        assertNotNull(dead.getDeletedAt());
        assertNull(alive.getDeletedAt());
    }

    @Test
    void exceptionOnOneSubscriptionDoesNotPropagate() {
        UUID userId = UUID.randomUUID();
        PushSubscription bad = sub(userId, "https://push.example.com/bad");
        PushSubscription alive = sub(userId, "https://push.example.com/alive");

        when(subscriptionRepository.findByUserIdAndDeletedAtIsNull(userId))
                .thenReturn(List.of(bad, alive));
        stub.scriptError(new RuntimeException("network exploded"));
        stub.scriptStatus(201);

        assertTrue(stub.sendToUser(userId, "t", "b", "/"));
        assertNull(bad.getDeletedAt());
        assertNull(alive.getDeletedAt());
    }
}
