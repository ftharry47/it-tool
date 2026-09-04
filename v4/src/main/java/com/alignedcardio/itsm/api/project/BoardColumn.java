package com.alignedcardio.itsm.api.project;

import java.util.List;
import java.util.UUID;

public record BoardColumn(
        UUID workflowStatusId,
        String status,
        String category,
        int displayOrder,
        List<IssueResponse> issues
) {
}
