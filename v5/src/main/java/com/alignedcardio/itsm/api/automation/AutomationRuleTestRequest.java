package com.alignedcardio.itsm.api.automation;

import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record AutomationRuleTestRequest(
        @NotNull
        Map<String, Object> samplePayload,

        UUID entityId
) {
}
