package com.alignedcardio.itsm.event;

import java.util.Map;
import java.util.UUID;

public interface DomainEvent {
    UUID orgId();

    String triggerEntity();

    String triggerType();

    UUID entityId();

    Map<String, Object> payload();
}
