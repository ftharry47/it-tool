package com.alignedcardio.itsm.api.project;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SprintResponse(
        UUID id,
        UUID projectId,
        String name,
        String goal,
        String status,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        OffsetDateTime completedAt
) {
}
