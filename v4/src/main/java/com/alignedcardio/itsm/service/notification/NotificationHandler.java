package com.alignedcardio.itsm.service.notification;

import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.event.DomainEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class NotificationHandler {

    private final NotificationService notificationService;

    public NotificationHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void handle(DomainEvent event, JsonNode action, UUID actingUserId) {
        String userIdText = action.get("userId").asText();
        UUID userId = resolveUserId(userIdText, event);
        String subject = template(action.get("subject").asText(), event);
        String body = template(action.get("body").asText(), event);
        String channel = action.hasNonNull("channel") ? action.get("channel").asText().toUpperCase() : "BOTH";

        NotificationRequest request = new NotificationRequest(
                event.orgId(),
                userId,
                "AUTOMATION",
                subject,
                body,
                event.triggerEntity(),
                event.entityId(),
                Notification.Channel.valueOf(channel));

        notificationService.send(request);
    }

    private UUID resolveUserId(String userIdText, DomainEvent event) {
        String placeholder = unwrapPlaceholder(userIdText);
        if (placeholder != null) {
            Object value = event.payload().get(placeholder);
            if (value instanceof UUID uuid) {
                return uuid;
            }
            if (value instanceof String str) {
                return UUID.fromString(str);
            }
            throw new IllegalArgumentException("Payload does not contain a valid userId placeholder: " + placeholder);
        }
        return UUID.fromString(userIdText);
    }

    private String template(String text, DomainEvent event) {
        String result = text;
        for (Map.Entry<String, Object> entry : event.payload().entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private String unwrapPlaceholder(String value) {
        if (value != null && value.startsWith("{{") && value.endsWith("}}")) {
            return value.substring(2, value.length() - 2).trim();
        }
        return null;
    }
}
