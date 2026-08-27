package com.alignedcardio.itsm.service.notification;

import com.alignedcardio.itsm.entity.Notification;

import java.util.UUID;

public record NotificationRequest(
        UUID orgId,
        UUID userId,
        String type,
        String subject,
        String body,
        String entityType,
        UUID entityId,
        Notification.Channel channel
) {
}
