package com.alignedcardio.itsm.api.project;

import com.alignedcardio.itsm.entity.IssueLink;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record IssueLinkCreateRequest(
        @NotNull UUID toIssueId,
        @NotNull IssueLink.LinkType linkType
) {
}
