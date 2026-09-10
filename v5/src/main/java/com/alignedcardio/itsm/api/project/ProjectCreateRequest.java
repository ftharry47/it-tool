package com.alignedcardio.itsm.api.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProjectCreateRequest(
        @NotBlank @Size(max = 10) String key,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        UUID leadId
) {
}
