package com.alignedcardio.itsm.api.incident;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IncidentCommentResponse(
        UUID id,
        String body,
        boolean isPublic,
        String author,
        String authorType,
        OffsetDateTime createdAt
) {
}
