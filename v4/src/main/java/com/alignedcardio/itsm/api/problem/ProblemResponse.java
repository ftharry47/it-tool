package com.alignedcardio.itsm.api.problem;

import com.alignedcardio.itsm.entity.Problem;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProblemResponse(
        UUID id,
        Long number,
        String title,
        String description,
        Problem.Status status,
        String rootCause,
        String workaround,
        UUID assigneeId,
        String assigneeName,
        OffsetDateTime resolvedAt,
        OffsetDateTime closedAt,
        OffsetDateTime createdAt
) {
}
