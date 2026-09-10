package com.alignedcardio.itsm.api.catalog;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CatalogItemResponse(
        UUID id,
        String name,
        String description,
        String category,
        String formSchema,
        boolean approvalRequired,
        UUID approverId,
        String approverName,
        String fulfillmentTasks,
        boolean active,
        OffsetDateTime createdAt
) {
}
