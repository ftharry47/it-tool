package com.alignedcardio.itsm.api.problem;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LinkIncidentRequest(
        @NotNull UUID incidentId
) {
}
