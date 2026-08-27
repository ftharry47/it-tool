package com.alignedcardio.itsm.service.notification;

import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.event.DomainEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NotificationHandler {

    private final NotificationService notificationService;

    public NotificationHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void handle(DomainEvent event, JsonNode action, UUID actingUserId) {
        UUID userId = UUID.fromString(action.get("userId").asText());
        String subject = action.get("subject").asText();
        String body = action.get("body").asText();
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
}
