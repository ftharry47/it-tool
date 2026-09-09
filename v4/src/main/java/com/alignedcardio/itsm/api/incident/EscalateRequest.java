package com.alignedcardio.itsm.api.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EscalateRequest(
        @NotNull UUID priorityId,
        @NotBlank String reason
) {
}
