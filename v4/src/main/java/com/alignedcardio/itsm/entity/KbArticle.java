package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

@Entity
@Table(name = "kb_article")
public class KbArticle extends BaseEntity {

    public enum Status { DRAFT, PENDING_REVIEW, PUBLISHED, ARCHIVED }

    @Column(name = "number", columnDefinition = "bigint not null default nextval('kb_article_number_seq')", insertable = false, updatable = false, nullable = false)
    private Long number;

    @NotNull
    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "category", length = 100)
    private String category;

    @NotNull
    @Column(name = "body", columnDefinition = "text", nullable = false)
    private String body;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private Status status = Status.DRAFT;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    @Column(name = "helpful_count", nullable = false)
    private int helpfulCount = 0;

    @Column(name = "not_helpful_count", nullable = false)
    private int notHelpfulCount = 0;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    public Long getNumber() {
        return number;
    }

    public void setNumber(Long number) {
        this.number = number;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public AppUser getAuthor() {
        return author;
    }

    public void setAuthor(AppUser author) {
        this.author = author;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public int getHelpfulCount() {
        return helpfulCount;
    }

    public void setHelpfulCount(int helpfulCount) {
        this.helpfulCount = helpfulCount;
    }

    public int getNotHelpfulCount() {
        return notHelpfulCount;
    }

    public void setNotHelpfulCount(int notHelpfulCount) {
        this.notHelpfulCount = notHelpfulCount;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(OffsetDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }
}
