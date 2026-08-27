package com.alignedcardio.itsm.api.project;

import com.alignedcardio.itsm.entity.IssueLink;

import java.util.UUID;

public record IssueLinkResponse(
        UUID id,
        UUID fromIssueId,
        UUID toIssueId,
        String toIssueKey,
        String toIssueSummary,
        IssueLink.LinkType linkType
) {
}
