package com.alignedcardio.itsm.event;

import java.util.Map;
import java.util.UUID;

public record IssueCreatedEvent(
        UUID orgId,
        UUID entityId,
        Map<String, Object> payload
) implements DomainEvent {
    @Override
    public String triggerEntity() {
        return "ISSUE";
    }

    @Override
    public String triggerType() {
        return "CREATED";
    }
}
