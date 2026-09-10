package com.alignedcardio.itsm.service.notification;

/**
 * Per-channel rendering of one notification event. Produced once by
 * NotificationTemplateBuilder so all three channels stay consistent.
 *
 * emailSubject/emailBody — full structure for email.
 * inAppSubject/inAppBody — compact, list-view friendly (stored on the row).
 * pushTitle/pushBody — short, OS-truncation-safe (~45 / ~120 chars).
 */
public record NotificationContent(
        String emailSubject,
        String emailBody,
        String inAppSubject,
        String inAppBody,
        String pushTitle,
        String pushBody
) {
}
