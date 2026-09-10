package com.alignedcardio.itsm.api.project;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProjectUpdateRequest(
        @Size(max = 255) String name,
        @Size(max = 1000) String description,
        UUID leadId,
        String status
) {
}
