package com.alignedcardio.itsm.api.sla;

import com.alignedcardio.itsm.entity.SlaInstance;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SlaInstanceDetailResponse(
        UUID id,
        String entityKind,
        UUID incidentId,
        Long incidentNumber,
        String incidentPriority,
        String incidentTitle,
        UUID serviceRequestId,
        String serviceRequestNumber,
        String serviceRequestTitle,
        String serviceRequestPriority,
        String fulfillerName,
        UUID problemId,
        String problemNumber,
        String problemTitle,
        UUID changeId,
        String changeNumber,
        String changeTitle,
        String policyName,
        String workflowType,
        OffsetDateTime responseDueAt,
        OffsetDateTime resolutionDueAt,
        OffsetDateTime responseMetAt,
        OffsetDateTime resolutionMetAt,
        OffsetDateTime pausedAt,
        int totalPausedMinutes,
        SlaInstance.BreachStatus breachStatus
) {
}
