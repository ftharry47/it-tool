package com.alignedcardio.itsm.api.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CommentCreateRequest(
        @NotBlank String body,
        @NotNull Boolean isPublic
) {
}
