package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "project")
public class Project extends BaseEntity {

    public enum Status { ACTIVE, ARCHIVED }

    @NotNull
    @Column(name = "key", length = 10, nullable = false)
    private String key;

    @NotNull
    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private AppUser lead;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private Status status = Status.ACTIVE;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public AppUser getLead() {
        return lead;
    }

    public void setLead(AppUser lead) {
        this.lead = lead;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
