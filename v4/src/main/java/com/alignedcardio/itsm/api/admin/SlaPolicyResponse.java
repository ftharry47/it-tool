package com.alignedcardio.itsm.api.admin;

import java.util.UUID;

// DTO for SLA policy responses — avoids serializing the lazy
// businessHoursCalendar association outside the transaction.
public record SlaPolicyResponse(
        UUID id,
        String name,
        String appliesTo,
        String priorityFilter,
        int responseTargetMinutes,
        int resolutionTargetMinutes,
        CalendarRef businessHoursCalendar
) {
    public record CalendarRef(UUID id, String name) {
    }
}
