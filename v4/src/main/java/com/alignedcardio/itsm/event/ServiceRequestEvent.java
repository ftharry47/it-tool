package com.alignedcardio.itsm.event;

import java.util.Map;
import java.util.UUID;

public record ServiceRequestEvent(
        UUID orgId,
        UUID entityId,
        String triggerType,
        Map<String, Object> payload
) implements DomainEvent {
    @Override
    public String triggerEntity() {
        return "SERVICE_REQUEST";
    }
}
