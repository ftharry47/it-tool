package com.alignedcardio.itsm.api.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID actorUserId,
        String action,
        String entityType,
        UUID entityId,
        String beforeState,
        String afterState,
        String ipAddress,
        OffsetDateTime createdAt
) {
}
