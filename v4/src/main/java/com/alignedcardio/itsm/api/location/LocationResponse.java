package com.alignedcardio.itsm.api.location;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LocationResponse(
        UUID id,
        String name,
        String address,
        UUID approvalManagerUserId,
        String approvalManagerName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
