package com.alignedcardio.itsm.api.project;

import com.alignedcardio.itsm.entity.WorkflowStatus;

import java.util.UUID;

public record WorkflowStatusResponse(
        UUID id,
        String name,
        WorkflowStatus.Category category,
        int displayOrder,
        boolean terminal
) {
}
