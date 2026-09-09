package com.alignedcardio.itsm.api.reporting;

import java.util.UUID;

public record TicketsByLocationResponse(
        UUID locationId,
        String locationName,
        long totalOpen,
        long openIncidents,
        long openServiceRequests,
        Integer oldestOpenDays,
        long resolvedCount,
        long breachedCount
) {
}
