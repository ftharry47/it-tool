package com.alignedcardio.itsm.api.kb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KbArticleCreateRequest(
        @NotBlank @Size(max = 500) String title,
        @Size(max = 100) String category,
        @NotBlank String body
) {
}
