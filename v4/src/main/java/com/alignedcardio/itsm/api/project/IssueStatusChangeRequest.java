package com.alignedcardio.itsm.api.project;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record IssueStatusChangeRequest(
        @NotNull UUID workflowStatusId
) {
}
