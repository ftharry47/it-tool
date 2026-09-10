package com.alignedcardio.itsm.service.notification;

import java.util.UUID;

public record NotificationPayload(
        UUID id,
        String type,
        String subject,
        String body,
        String entityType,
        UUID entityId,
        String createdAt
) {
}
