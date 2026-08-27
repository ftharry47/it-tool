package com.alignedcardio.itsm.api.automation;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AutomationRuleResponse(
        UUID id,
        UUID orgId,
        String name,
        String description,
        String triggerType,
        String triggerEntity,
        String triggerConfig,
        String conditions,
        String actions,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
