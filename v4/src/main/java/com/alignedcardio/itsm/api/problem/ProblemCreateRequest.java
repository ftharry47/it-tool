package com.alignedcardio.itsm.api.problem;

import jakarta.validation.constraints.NotBlank;

public record ProblemCreateRequest(
        @NotBlank String title,
        String description,
        java.util.UUID assigneeId
) {
}
