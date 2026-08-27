package com.alignedcardio.itsm.api.project;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String key,
        String name,
        String description,
        UUID leadId,
        String leadName,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
