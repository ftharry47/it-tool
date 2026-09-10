package com.alignedcardio.itsm.api.automation;

import jakarta.validation.constraints.Size;

public record AutomationRuleUpdateRequest(
        @Size(max = 255) String name,
        @Size(max = 1000) String description,
        String triggerType,
        String triggerEntity,
        String triggerConfig,
        String conditions,
        String actions,
        Boolean active
) {
}
