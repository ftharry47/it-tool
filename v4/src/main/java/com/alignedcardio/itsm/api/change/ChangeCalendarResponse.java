package com.alignedcardio.itsm.api.change;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChangeCalendarResponse(
        List<ChangeCalendarItem> changes,
        List<Conflict> conflicts
) {

    public record ChangeCalendarItem(
            UUID id,
            Long number,
            String title,
            OffsetDateTime plannedStart,
            OffsetDateTime plannedEnd
    ) {
    }

    public record Conflict(
            UUID changeA,
            UUID changeB
    ) {
    }
}
