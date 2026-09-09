package com.alignedcardio.itsm.api.location;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record LocationRequest(
        @NotBlank String name,
        String address,
        UUID approvalManagerUserId
) {
}
