package com.alignedcardio.itsm.api.incident;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EscalationEntry(
        UUID incidentId,
        Long incidentNumber,
        String incidentTitle,
        String action,
        String actorName,
        String detail,
        OffsetDateTime createdAt
) {
}
