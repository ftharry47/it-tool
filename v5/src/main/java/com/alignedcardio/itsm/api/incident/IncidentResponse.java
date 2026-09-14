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
        UUID assigneeId,
        UUID assignmentTeamId,
        String assignmentTeamName,
        String closingNotes,
        String location,
        UUID locationId,
        String phone,
        Integer estimatedMinutes,
        Integer totalLoggedMinutes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        boolean hasBeenTierEscalated,
        boolean tierEscalatedFromMe
) {
}
