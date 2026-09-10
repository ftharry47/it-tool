package com.alignedcardio.itsm.event;

import java.util.Map;
import java.util.UUID;

public record IncidentStatusChangedEvent(
        UUID orgId,
        UUID entityId,
        Map<String, Object> payload
) implements DomainEvent {
    @Override
    public String triggerEntity() {
        return "INCIDENT";
    }

    @Override
    public String triggerType() {
        return "STATUS_CHANGED";
    }
}
