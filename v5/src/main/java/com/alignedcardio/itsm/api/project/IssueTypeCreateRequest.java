package com.alignedcardio.itsm.api.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IssueTypeCreateRequest(
        @NotBlank @Size(max = 64) String name,
        @Size(max = 500) String description,
        @Size(max = 64) String icon,
        @Size(max = 32) String color
) {
}
