package com.alignedcardio.itsm.api.change;

import com.alignedcardio.itsm.entity.ChangeRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChangeResponse(
        UUID id,
        Long number,
        String title,
        String description,
        ChangeRequest.ChangeType changeType,
        ChangeRequest.Risk risk,
        ChangeRequest.Status status,
        UUID requestedById,
        String requestedByName,
        OffsetDateTime plannedStart,
        OffsetDateTime plannedEnd,
        String rollbackPlan,
        String postImplementationReview,
        UUID linkedProblemId,
        List<ChangeApprovalResponse> approvals,
        OffsetDateTime createdAt
) {
}
