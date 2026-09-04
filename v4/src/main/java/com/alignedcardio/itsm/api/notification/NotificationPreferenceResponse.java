package com.alignedcardio.itsm.api.notification;

public record NotificationPreferenceResponse(
        boolean inAppEnabled,
        boolean emailEnabled,
        String emailAddress,
        String digestMode,
        boolean notifyStatusChange,
        boolean notifyAssignment,
        boolean notifyComment,
        boolean notifyMention
) {
}
