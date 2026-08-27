package com.alignedcardio.itsm.api.incident;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignRequest(
        @NotNull UUID assigneeId
) {
}
