package com.alignedcardio.itsm.service.reporting;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AdHocQueryResponse(
        UUID orgId,
        String entity,
        String groupBy,
        List<Map<String, Object>> rows
) {
}
