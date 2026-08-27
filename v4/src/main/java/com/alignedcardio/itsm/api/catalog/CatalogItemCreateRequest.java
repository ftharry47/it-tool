package com.alignedcardio.itsm.api.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CatalogItemCreateRequest(
        @NotBlank String name,
        String description,
        String category,
        @NotNull String formSchema,
        boolean approvalRequired,
        UUID approverId,
        String fulfillmentTasks,
        boolean active
) {
}
