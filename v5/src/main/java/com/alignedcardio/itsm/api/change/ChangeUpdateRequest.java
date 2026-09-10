package com.alignedcardio.itsm.api.change;

import com.alignedcardio.itsm.entity.ChangeRequest;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChangeUpdateRequest(
        String title,
        String description,
        ChangeRequest.ChangeType changeType,
        ChangeRequest.Risk risk,
        UUID requestedById,
        UUID assigneeId,
        OffsetDateTime plannedStart,
        OffsetDateTime plannedEnd,
        String rollbackPlan,
        String postImplementationReview,
        UUID linkedProblemId,
        UUID locationId
) {
}
