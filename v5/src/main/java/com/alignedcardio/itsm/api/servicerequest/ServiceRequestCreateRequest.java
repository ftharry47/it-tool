package com.alignedcardio.itsm.api.servicerequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceRequestCreateRequest(
        @NotNull UUID catalogItemId,
        @NotNull String formData,
        OffsetDateTime neededBy,
        UUID locationId,
        @NotBlank(message = "Phone number is required") String phone,
        UUID priorityId
) {
}
