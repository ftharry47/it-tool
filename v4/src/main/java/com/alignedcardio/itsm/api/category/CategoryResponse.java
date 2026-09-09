package com.alignedcardio.itsm.api.category;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String description,
        int displayOrder,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
