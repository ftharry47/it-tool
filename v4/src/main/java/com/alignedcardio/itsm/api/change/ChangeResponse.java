package com.alignedcardio.itsm.api.change;

import com.alignedcardio.itsm.entity.ChangeRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChangeResponse(
        UUID id,
        String number,
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
        UUID locationId,
        String locationName,
        List<ChangeApprovalResponse> approvals,
        OffsetDateTime createdAt
) {
}
