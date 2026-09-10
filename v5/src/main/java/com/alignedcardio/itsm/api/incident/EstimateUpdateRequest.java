package com.alignedcardio.itsm.api.incident;

import jakarta.validation.constraints.Min;

public record EstimateUpdateRequest(
        @Min(0) Integer estimatedMinutes
) {
}
