package com.alignedcardio.itsm.api.project;

import java.util.UUID;

public record WorkflowTransitionResponse(
        UUID id,
        UUID fromStatusId,
        String fromStatusName,
        UUID toStatusId,
        String toStatusName,
        String screen
) {
}
