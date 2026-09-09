package com.alignedcardio.itsm.api.servicerequest;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceRequestActivityResponse(
        UUID id,
        String action,
        UUID actorUserId,
        String actorName,
        String beforeState,
        String afterState,
        OffsetDateTime createdAt
) {
}
