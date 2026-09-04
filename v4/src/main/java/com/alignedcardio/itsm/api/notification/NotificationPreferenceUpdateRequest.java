package com.alignedcardio.itsm.api.notification;

import jakarta.validation.constraints.NotNull;

public record NotificationPreferenceUpdateRequest(
        @NotNull Boolean inAppEnabled,
        @NotNull Boolean emailEnabled,
        String emailAddress,
        @NotNull String digestMode,
        @NotNull Boolean notifyStatusChange,
        @NotNull Boolean notifyAssignment,
        @NotNull Boolean notifyComment,
        @NotNull Boolean notifyMention
) {
}
