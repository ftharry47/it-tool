package com.alignedcardio.itsm.events;

import com.alignedcardio.itsm.entity.SlaInstance;

import java.util.UUID;

public record SlaBreachStatusChangedEvent(
        UUID slaInstanceId,
        SlaInstance.BreachStatus fromStatus,
        SlaInstance.BreachStatus toStatus,
        UUID incidentId
) {
}
