package com.alignedcardio.itsm.api.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record WorkflowCreateRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 500) String description,
        UUID projectId
) {
}
