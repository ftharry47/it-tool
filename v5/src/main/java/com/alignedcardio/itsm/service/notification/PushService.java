package com.alignedcardio.itsm.service.notification;

import com.alignedcardio.itsm.entity.PushSubscription;
import com.alignedcardio.itsm.repository.PushSubscriptionRepository;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.Security;
import java.util.List;

/**
 * Delivers Web Push notifications via VAPID. If VAPID keys are not configured
 * the service is a clean no-op — push delivery is skipped entirely.
 */
@Service
public class PushService {

    private static final Logger logger = LoggerFactory.getLogger(PushService.class);

    private final PushSubscriptionRepository subscriptionRepository;

    @Value("${webpush.vapid.public-key:}")
    private String vapidPublicKey;

    @Value("${webpush.vapid.private-key:}")
    private String vapidPrivateKey;

    @Value("${webpush.vapid.subject:mailto:it-support@alignedcardio.com}")
    private String vapidSubject;

    private nl.martijndwars.webpush.PushService pushService;

    public PushService(PushSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @PostConstruct
    void init() {
        if (vapidPublicKey == null || vapidPublicKey.isBlank()
                || vapidPrivateKey == null || vapidPrivateKey.isBlank()) {
            logger.info("VAPID keys not configured — push notifications disabled");
            return;
        }
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            pushService = new nl.martijndwars.webpush.PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
            logger.info("Web Push service initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize Web Push service", e);
            pushService = null;
        }
    }

    public boolean isEnabled() {
        return pushService != null;
    }

    public String getPublicKey() {
        return vapidPublicKey;
    }

    /**
     * Sends a push notification to all of the user's active subscriptions.
     * Each subscription is isolated — one failure never affects the others.
     * Returns true if at least one subscription received the push.
     */
    public boolean sendToUser(java.util.UUID userId, String title, String body, String url) {
        return sendToUser(userId, title, body, url, null, null);
    }

    /**
     * Same as {@link #sendToUser(UUID, String, String, String)} but also
     * embeds the affected entity so the service worker can tell open tabs
     * which query data to refresh.
     */
    public boolean sendToUser(java.util.UUID userId, String title, String body, String url,
                              String entityType, java.util.UUID entityId) {
        if (!isEnabled()) {
            return false;
        }
        List<PushSubscription> subs = subscriptionRepository.findByUserIdAndDeletedAtIsNull(userId);
        if (subs.isEmpty()) {
            return false;
        }

        String payload = buildPayload(title, body, url, entityType, entityId);
        boolean anySent = false;

        for (PushSubscription sub : subs) {
            try {
                int status = deliver(sub, payload);
                if (status >= 200 && status < 300) {
                    anySent = true;
                } else if (status == 404 || status == 410) {
                    logger.info("Push subscription {} expired (HTTP {}), soft-deleting", sub.getId(), status);
                    sub.softDelete();
                    subscriptionRepository.save(sub);
                } else {
                    logger.warn("Push to subscription {} returned HTTP {}", sub.getId(), status);
                }
            } catch (Exception e) {
                logger.error("Push to subscription {} failed", sub.getId(), e);
            }
        }
        return anySent;
    }

    /**
     * Performs the actual web-push HTTP call for one subscription.
     * Package-private so tests can override without mocking the library.
     */
    int deliver(PushSubscription sub, String payload) throws Exception {
        nl.martijndwars.webpush.Notification notification = new nl.martijndwars.webpush.Notification(
                sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload);
        HttpResponse response = pushService.send(notification);
        return response.getStatusLine().getStatusCode();
    }

    private String buildPayload(String title, String body, String url, String entityType, java.util.UUID entityId) {
        // Minimal JSON payload — the service worker reads title/body/url and
        // forwards entityType/entityId to open tabs for query invalidation.
        return String.format(
                "{\"title\":\"%s\",\"body\":\"%s\",\"url\":\"%s\",\"entityType\":\"%s\",\"entityId\":\"%s\"}",
                escapeJson(title), escapeJson(body), escapeJson(url != null ? url : "/"),
                escapeJson(entityType), entityId != null ? entityId.toString() : "");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
