package com.alignedcardio.itsm.api.change;

import com.alignedcardio.itsm.entity.ChangeApproval;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChangeApprovalResponse(
        UUID id,
        UUID approverId,
        String approverName,
        int sequenceOrder,
        ChangeApproval.Status status,
        OffsetDateTime decidedAt,
        String comment
) {
}
