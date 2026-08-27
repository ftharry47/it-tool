package com.alignedcardio.itsm.api.change;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChangeApprovalRequest(
        @NotNull UUID approverId,
        int sequenceOrder,
        String comment
) {
}
