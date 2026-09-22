package com.alignedcardio.itsm.api.incident;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IncidentSummary(
        UUID id,
        Long number,
        String title,
        String status,
        String priority,
        String category,
        String location,
        String phone,
        String requester,
        String assignee,
        UUID assigneeId,
        OffsetDateTime createdAt,
        String slaBreachStatus,
        OffsetDateTime responseDueAt,
        OffsetDateTime resolutionDueAt,
        OffsetDateTime responseMetAt,
        OffsetDateTime resolutionMetAt,
        boolean hasBeenTierEscalated,
        boolean legacyImport
) {
}
