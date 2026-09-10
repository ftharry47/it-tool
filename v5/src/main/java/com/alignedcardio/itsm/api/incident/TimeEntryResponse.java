package com.alignedcardio.itsm.api.incident;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TimeEntryResponse(
        UUID id,
        Integer timeSpentMinutes,
        String description,
        String loggedBy,
        OffsetDateTime loggedAt
) {
}
