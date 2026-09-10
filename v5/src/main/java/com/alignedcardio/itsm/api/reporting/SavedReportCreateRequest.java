package com.alignedcardio.itsm.api.reporting;

import com.alignedcardio.itsm.service.reporting.AdHocQueryFilter;
import com.alignedcardio.itsm.service.reporting.AdHocQueryRequest;

import java.util.List;

public record SavedReportCreateRequest(
        String name,
        String entity,
        List<AdHocQueryFilter> filters,
        String groupBy,
        AdHocQueryRequest.DateRange dateRange
) {
}
