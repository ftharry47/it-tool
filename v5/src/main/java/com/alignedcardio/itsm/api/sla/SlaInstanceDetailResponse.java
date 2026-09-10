package com.alignedcardio.itsm.api.sla;

import com.alignedcardio.itsm.entity.SlaInstance;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SlaInstanceDetailResponse(
        UUID id,
        UUID incidentId,
        Long incidentNumber,
        String incidentPriority,
        String incidentTitle,
        UUID serviceRequestId,
        String serviceRequestNumber,
        String serviceRequestTitle,
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
