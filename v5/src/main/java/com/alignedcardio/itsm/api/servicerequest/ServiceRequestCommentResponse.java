package com.alignedcardio.itsm.api.servicerequest;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceRequestCommentResponse(
        UUID id,
        String body,
        boolean isPublic,
        String author,
        String authorType,
        OffsetDateTime createdAt
) {
}
