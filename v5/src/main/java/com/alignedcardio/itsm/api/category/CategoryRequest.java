package com.alignedcardio.itsm.api.category;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
        @NotBlank String name,
        String description,
        Integer displayOrder,
        String status
) {
}
