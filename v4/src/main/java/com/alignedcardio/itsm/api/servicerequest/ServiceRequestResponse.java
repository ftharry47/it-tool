package com.alignedcardio.itsm.api.servicerequest;

import com.alignedcardio.itsm.entity.ServiceRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ServiceRequestResponse(
        UUID id,
        String number,
        UUID catalogItemId,
        String catalogItemName,
        UUID requesterId,
        String requesterName,
        ServiceRequest.Status status,
        String formData,
        boolean approvalRequired,
        UUID approverId,
        String approverName,
        ServiceRequest.ApprovalDecision approvalDecision,
        String approvalComment,
        OffsetDateTime decidedAt,
        OffsetDateTime neededBy,
        List<FulfillmentTaskResponse> tasks,
        OffsetDateTime createdAt
) {
}
