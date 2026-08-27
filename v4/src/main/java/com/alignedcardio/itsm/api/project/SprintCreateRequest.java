package com.alignedcardio.itsm.api.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SprintCreateRequest(
        @NotNull UUID projectId,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 1000) String goal,
        OffsetDateTime startDate,
        OffsetDateTime endDate
) {
}
