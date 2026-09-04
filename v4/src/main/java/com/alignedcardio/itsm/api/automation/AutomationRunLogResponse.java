package com.alignedcardio.itsm.api.automation;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AutomationRunLogResponse(
        UUID id,
        UUID ruleId,
        String entityType,
        String entityId,
        String triggeredEvent,
        String status,
        String output,
        String error,
        OffsetDateTime executedAt
) {
}
