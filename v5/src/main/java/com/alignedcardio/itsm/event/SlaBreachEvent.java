package com.alignedcardio.itsm.event;

import java.util.Map;
import java.util.UUID;

public record SlaBreachEvent(
        UUID orgId,
        UUID entityId,
        String triggerEntity,
        String triggerType,
        Map<String, Object> payload
) implements DomainEvent {

    public SlaBreachEvent(UUID orgId,
                          UUID slaInstanceId,
                          String fromStatus,
                          String toStatus,
                          UUID incidentId,
                          Object incidentNumber) {
        this(orgId,
                slaInstanceId,
                "SLA",
                "SLA_BREACH_RISK",
                Map.of(
                        "slaInstanceId", slaInstanceId,
                        "from", fromStatus,
                        "to", toStatus,
                        "incidentId", incidentId != null ? incidentId : "",
                        "incidentNumber", incidentNumber != null ? String.valueOf(incidentNumber) : ""));
    }
}
