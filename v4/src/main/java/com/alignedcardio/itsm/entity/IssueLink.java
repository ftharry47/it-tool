package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "issue_link")
public class IssueLink extends BaseEntity {

    public enum LinkType { BLOCKS, BLOCKED_BY, RELATES_TO, DUPLICATES }

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_issue_id", nullable = false)
    private Issue fromIssue;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_issue_id", nullable = false)
    private Issue toIssue;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", length = 32, nullable = false)
    private LinkType linkType;

    public Issue getFromIssue() {
        return fromIssue;
    }

    public void setFromIssue(Issue fromIssue) {
        this.fromIssue = fromIssue;
    }

    public Issue getToIssue() {
        return toIssue;
    }

    public void setToIssue(Issue toIssue) {
        this.toIssue = toIssue;
    }

    public LinkType getLinkType() {
        return linkType;
    }

    public void setLinkType(LinkType linkType) {
        this.linkType = linkType;
    }
}
