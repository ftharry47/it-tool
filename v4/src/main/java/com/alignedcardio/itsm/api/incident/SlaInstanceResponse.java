package com.alignedcardio.itsm.api.incident;

import com.alignedcardio.itsm.entity.SlaInstance;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SlaInstanceResponse(
        UUID id,
        UUID incidentId,
        String policyName,
        OffsetDateTime responseDueAt,
        OffsetDateTime resolutionDueAt,
        OffsetDateTime responseMetAt,
        OffsetDateTime resolutionMetAt,
        OffsetDateTime pausedAt,
        int totalPausedMinutes,
        SlaInstance.BreachStatus breachStatus
) {
}
