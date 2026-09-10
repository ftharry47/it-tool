package com.alignedcardio.itsm.api.project;

import com.alignedcardio.itsm.entity.Issue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record IssueCreateRequest(
        @NotNull UUID projectId,
        UUID issueTypeId,
        @NotNull UUID workflowId,
        @NotNull UUID workflowStatusId,
        UUID sprintId,
        UUID epicId,
        UUID parentIssueId,
        @NotBlank @Size(max = 255) String summary,
        @Size(max = 4000) String description,
        UUID assigneeId,
        Integer storyPoints,
        Issue.Priority priority
) {
}
