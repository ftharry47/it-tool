package com.alignedcardio.itsm.api.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LinkCreateRequest(
        @NotNull UUID toIncidentId,
        @NotBlank String linkType
) {
}
