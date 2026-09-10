package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "issue_comment")
public class IssueComment extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false)
    private Issue issue;

    @NotNull
    @Column(name = "body", length = 4000, nullable = false)
    private String body;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", length = 20, nullable = false)
    private CommentAuthorType authorType = CommentAuthorType.USER;

    public Issue getIssue() {
        return issue;
    }

    public void setIssue(Issue issue) {
        this.issue = issue;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public CommentAuthorType getAuthorType() {
        return authorType;
    }

    public void setAuthorType(CommentAuthorType authorType) {
        this.authorType = authorType;
    }
}
