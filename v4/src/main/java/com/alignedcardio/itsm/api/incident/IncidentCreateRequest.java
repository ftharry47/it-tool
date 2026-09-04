package com.alignedcardio.itsm.api.incident;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record IncidentCreateRequest(
        @NotBlank String title,
        String description,
        @Min(1) @Max(5) Integer impact,
        @Min(1) @Max(5) Integer urgency,
        @NotNull UUID categoryId,
        UUID priorityId,
        String location,
        String phone
) {
}
