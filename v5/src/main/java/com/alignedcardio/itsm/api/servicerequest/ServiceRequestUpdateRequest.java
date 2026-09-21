package com.alignedcardio.itsm.api.servicerequest;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * SUPER_ADMIN record correction. Editable: location, priority, phone,
 * needed-by, and the catalog form data. The catalog item, requester,
 * approval fields, and status are deliberately not editable here.
 */
public record ServiceRequestUpdateRequest(
        UUID locationId,
        UUID priorityId,
        String phone,
        OffsetDateTime neededBy,
        String formData
) {
}
