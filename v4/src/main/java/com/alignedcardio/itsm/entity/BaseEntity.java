package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Where;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Base entity that enforces the global data model conventions.
 * All tenant-scoped tables extend this class.
 */
@MappedSuperclass
@Where(clause = "deleted_at IS NULL")
public abstract class BaseEntity implements Serializable {

    public static final UUID DEFAULT_ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "org_id", columnDefinition = "uuid", nullable = false)
    private UUID orgId = DEFAULT_ORG_ID;

    @Column(name = "created_at", columnDefinition = "timestamptz not null default now()", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "timestamptz not null default now()", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy = SYSTEM_USER_ID;

    @Column(name = "updated_by", columnDefinition = "uuid")
    private UUID updatedBy = SYSTEM_USER_ID;

    @Column(name = "deleted_at", columnDefinition = "timestamptz")
    private OffsetDateTime deletedAt;

    @PrePersist
    public void onPrePersist() {
        if (orgId == null) {
            orgId = DEFAULT_ORG_ID;
        }
        if (createdBy == null) {
            createdBy = SYSTEM_USER_ID;
        }
        if (updatedBy == null) {
            updatedBy = SYSTEM_USER_ID;
        }
    }

    @PreUpdate
    public void onPreUpdate() {
        if (updatedBy == null) {
            updatedBy = SYSTEM_USER_ID;
        }
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }

    public void restore() {
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public void setOrgId(UUID orgId) {
        this.orgId = orgId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
