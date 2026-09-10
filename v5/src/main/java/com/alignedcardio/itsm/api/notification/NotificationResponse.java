package com.alignedcardio.itsm.api.notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String subject,
        String body,
        String entityType,
        UUID entityId,
        String channel,
        boolean read,
        OffsetDateTime readAt,
        OffsetDateTime createdAt
) {
}
