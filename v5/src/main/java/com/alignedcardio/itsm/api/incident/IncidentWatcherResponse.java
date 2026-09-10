package com.alignedcardio.itsm.api.incident;

import java.util.UUID;

public record IncidentWatcherResponse(
        UUID id,
        UUID userId,
        String displayName
) {
}
