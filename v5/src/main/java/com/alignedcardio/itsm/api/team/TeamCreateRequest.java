package com.alignedcardio.itsm.api.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamCreateRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 1000)
        String description
) {
}
