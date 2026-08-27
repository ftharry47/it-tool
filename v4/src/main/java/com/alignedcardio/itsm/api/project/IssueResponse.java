package com.alignedcardio.itsm.api.project;

import com.alignedcardio.itsm.entity.Issue;
import com.alignedcardio.itsm.entity.WorkflowStatus;

import java.util.UUID;

public record IssueResponse(
        UUID id,
        UUID projectId,
        String key,
        UUID issueTypeId,
        String issueTypeName,
        UUID workflowId,
        UUID workflowStatusId,
        String workflowStatusName,
        WorkflowStatus.Category workflowStatusCategory,
        UUID sprintId,
        UUID epicId,
        UUID parentIssueId,
        String summary,
        String description,
        UUID assigneeId,
        String assigneeName,
        UUID reporterId,
        String reporterName,
        Integer storyPoints,
        Integer remainingPoints,
        Issue.Priority priority
) {
}
