package com.alignedcardio.itsm.service.reporting;

import java.time.OffsetDateTime;
import java.util.List;

public record AdHocQueryRequest(
        String entity,
        List<AdHocQueryFilter> filters,
        String groupBy,
        DateRange dateRange,
        /** When true, return real matching rows instead of aggregate counts. */
        Boolean detailed,
        Integer page,
        Integer pageSize
) {
    public boolean isDetailed() {
        return Boolean.TRUE.equals(detailed);
    }

    public int pageOrDefault() {
        return page == null || page < 0 ? 0 : page;
    }

    public int pageSizeOrDefault() {
        if (pageSize == null || pageSize < 1) return 50;
        return Math.min(pageSize, 200);
    }

    public record DateRange(
            OffsetDateTime from,
            OffsetDateTime to,
            /** Optional per-entity date field (e.g. resolvedAt); defaults to createdAt. */
            String dateField
    ) {
    }
}
