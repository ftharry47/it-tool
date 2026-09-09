package com.alignedcardio.itsm.api.change;

import com.alignedcardio.itsm.entity.ChangeRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChangeCreateRequest(
        @NotBlank String title,
        String description,
        @NotNull ChangeRequest.ChangeType changeType,
        @NotNull ChangeRequest.Risk risk,
        UUID requestedById,
        OffsetDateTime plannedStart,
        OffsetDateTime plannedEnd,
        String rollbackPlan,
        UUID linkedProblemId,
        UUID locationId
) {
}
