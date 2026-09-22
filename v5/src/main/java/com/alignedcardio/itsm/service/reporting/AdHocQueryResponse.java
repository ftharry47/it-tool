package com.alignedcardio.itsm.service.reporting;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AdHocQueryResponse(
        UUID orgId,
        String entity,
        String groupBy,
        List<Map<String, Object>> rows,
        /** Present in detailed mode: total matching rows across all pages. */
        Long total,
        Integer page,
        Integer pageSize
) {
    public AdHocQueryResponse(UUID orgId, String entity, String groupBy, List<Map<String, Object>> rows) {
        this(orgId, entity, groupBy, rows, null, null, null);
    }
}
