package com.alignedcardio.itsm.api.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IssueCommentCreateRequest(
        @NotBlank @Size(max = 4000) String body
) {
}
