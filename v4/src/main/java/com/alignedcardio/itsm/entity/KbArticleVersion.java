package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

@Entity
@Table(name = "kb_article_version")
public class KbArticleVersion extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kb_article_id", nullable = false)
    private KbArticle kbArticle;

    @Column(name = "version", nullable = false)
    private int version;

    @NotNull
    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "category", length = 100)
    private String category;

    @NotNull
    @Column(name = "body", columnDefinition = "text", nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public KbArticle getKbArticle() {
        return kbArticle;
    }

    public void setKbArticle(KbArticle kbArticle) {
        this.kbArticle = kbArticle;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
