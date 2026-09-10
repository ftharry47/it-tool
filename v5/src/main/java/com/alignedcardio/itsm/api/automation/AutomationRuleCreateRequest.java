package com.alignedcardio.itsm.api.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AutomationRuleCreateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @NotBlank String triggerType,
        @NotBlank String triggerEntity,
        @NotNull String triggerConfig,
        @NotNull String conditions,
        @NotNull String actions,
        boolean active
) {
}
