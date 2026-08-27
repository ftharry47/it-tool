package com.alignedcardio.itsm.api.incident;

import java.util.UUID;

public record IncidentLinkResponse(
        UUID id,
        UUID toIncidentId,
        String linkType,
        Long toIncidentNumber
) {
}
