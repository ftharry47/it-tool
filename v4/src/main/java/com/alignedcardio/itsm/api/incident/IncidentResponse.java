package com.alignedcardio.itsm.api.incident;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        Long number,
        String title,
        String description,
        String status,
        String priority,
        String category,
        String requester,
        String assignee,
        String location,
        String phone,
        Integer estimatedMinutes,
        Integer totalLoggedMinutes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
