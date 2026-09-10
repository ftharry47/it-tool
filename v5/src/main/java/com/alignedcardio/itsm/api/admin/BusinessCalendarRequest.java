package com.alignedcardio.itsm.api.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BusinessCalendarRequest(
        @NotBlank String name,
        @NotBlank String timezone,
        @NotNull String workingHours,
        @NotNull String holidays
) {
}
