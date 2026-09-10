package com.alignedcardio.itsm.api.project;

import com.alignedcardio.itsm.entity.WorkflowStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkflowStatusCreateRequest(
        @NotBlank @Size(max = 64) String name,
        @NotNull WorkflowStatus.Category category,
        int displayOrder,
        boolean terminal
) {
}
