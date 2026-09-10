package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "incident_comment")
public class IncidentComment extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    @NotNull
    @Column(name = "body", length = 4000, nullable = false)
    private String body;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", length = 20, nullable = false)
    private CommentAuthorType authorType = CommentAuthorType.USER;

    public Incident getIncident() {
        return incident;
    }

    public void setIncident(Incident incident) {
        this.incident = incident;
    }

    public AppUser getAuthor() {
        return author;
    }

    public void setAuthor(AppUser author) {
        this.author = author;
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
