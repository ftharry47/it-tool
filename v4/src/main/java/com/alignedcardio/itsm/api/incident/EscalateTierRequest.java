package com.alignedcardio.itsm.api.incident;

import jakarta.validation.constraints.NotBlank;

public record EscalateTierRequest(
        @NotBlank String reason
) {
}
