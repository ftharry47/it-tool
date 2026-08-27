package com.alignedcardio.itsm.api.project;

import java.util.List;

public record BoardColumn(
        String status,
        String category,
        int displayOrder,
        List<IssueResponse> issues
) {
}
