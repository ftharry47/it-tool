package com.alignedcardio.itsm.api.project;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WorkflowTransitionCreateRequest(
        @NotNull UUID fromStatusId,
        @NotNull UUID toStatusId,
        String screen
) {
}
