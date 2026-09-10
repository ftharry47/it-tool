package com.alignedcardio.itsm.api.problem;

import jakarta.validation.constraints.NotBlank;

public record ProblemStatusUpdateRequest(
        @NotBlank String status
) {
}
