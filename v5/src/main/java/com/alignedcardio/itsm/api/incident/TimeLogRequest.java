package com.alignedcardio.itsm.api.incident;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TimeLogRequest(
        @NotNull @Min(1) Integer timeSpentMinutes,
        String description
) {
}
