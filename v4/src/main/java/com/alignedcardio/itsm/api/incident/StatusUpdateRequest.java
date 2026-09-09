package com.alignedcardio.itsm.api.incident;

import jakarta.validation.constraints.NotBlank;

public record StatusUpdateRequest(
        @NotBlank String status,
        String closingNotes
) {
}
