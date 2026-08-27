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
        String requester,
        String assignee,
        OffsetDateTime createdAt
) {
}
