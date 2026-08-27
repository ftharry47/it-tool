package com.alignedcardio.itsm.service.reporting;

import java.time.OffsetDateTime;
import java.util.List;

public record AdHocQueryRequest(
        String entity,
        List<AdHocQueryFilter> filters,
        String groupBy,
        DateRange dateRange
) {

    public record DateRange(
            OffsetDateTime from,
            OffsetDateTime to
    ) {
    }
}
