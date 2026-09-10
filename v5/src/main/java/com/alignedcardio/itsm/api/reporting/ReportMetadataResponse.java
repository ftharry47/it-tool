package com.alignedcardio.itsm.api.reporting;

import java.util.List;
import java.util.Map;

public record ReportMetadataResponse(
        List<String> entities,
        Map<String, List<String>> fieldsByEntity,
        List<String> operators,
        Map<String, String> dateFieldByEntity
) {
}
