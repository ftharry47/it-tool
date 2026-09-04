package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "time_entry", indexes = {
        @Index(name = "idx_time_entry_entity", columnList = "entity_type,entity_id")
})
public class TimeEntry extends BaseEntity {

    @NotNull
    @Column(name = "entity_type", length = 50, nullable = false)
    private String entityType;

    @NotNull
    @Column(name = "entity_id", columnDefinition = "uuid", nullable = false)
    private UUID entityId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @NotNull
    @Column(name = "time_spent_minutes", nullable = false)
    private int timeSpentMinutes;

    @Column(name = "description")
    private String description;

    @NotNull
    @Column(name = "logged_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime loggedAt;

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public int getTimeSpentMinutes() {
        return timeSpentMinutes;
    }

    public void setTimeSpentMinutes(int timeSpentMinutes) {
        this.timeSpentMinutes = timeSpentMinutes;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OffsetDateTime getLoggedAt() {
        return loggedAt;
    }

    public void setLoggedAt(OffsetDateTime loggedAt) {
        this.loggedAt = loggedAt;
    }
}
