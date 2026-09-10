package com.alignedcardio.itsm.api.project;

import java.util.List;
import java.util.UUID;

public record WorkflowResponse(
        UUID id,
        String name,
        String description,
        UUID projectId,
        List<WorkflowStatusResponse> statuses,
        List<WorkflowTransitionResponse> transitions
) {
}
