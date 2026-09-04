package com.alignedcardio.itsm.api.incident;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record IncidentUpdateRequest(
        @NotBlank String title,
        String description,
        @NotBlank String status,
        @Min(1) @Max(5) Integer impact,
        @Min(1) @Max(5) Integer urgency,
        UUID priorityId,
        UUID categoryId,
        UUID assigneeId
) {
}
