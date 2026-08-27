package com.alignedcardio.itsm.api.incident;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record IncidentUpdateRequest(
        @NotBlank String title,
        String description,
        @NotBlank String status,
        UUID priorityId,
        UUID categoryId,
        UUID assigneeId
) {
}
