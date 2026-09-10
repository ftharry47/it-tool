package com.alignedcardio.itsm.api.project;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IssueCommentResponse(
        UUID id,
        UUID issueId,
        String body,
        UUID createdBy,
        boolean isPublic,
        String authorType,
        OffsetDateTime createdAt
) {
}
