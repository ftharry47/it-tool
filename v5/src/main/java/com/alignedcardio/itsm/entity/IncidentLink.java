package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "incident_link")
public class IncidentLink extends BaseEntity {

    public enum LinkType {
        DUPLICATE, RELATED, BLOCKS, BLOCKED_BY
    }

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_incident_id", nullable = false)
    private Incident fromIncident;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_incident_id", nullable = false)
    private Incident toIncident;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", length = 50, nullable = false)
    private LinkType linkType;

    public Incident getFromIncident() {
        return fromIncident;
    }

    public void setFromIncident(Incident fromIncident) {
        this.fromIncident = fromIncident;
    }

    public Incident getToIncident() {
        return toIncident;
    }

    public void setToIncident(Incident toIncident) {
        this.toIncident = toIncident;
    }

    public LinkType getLinkType() {
        return linkType;
    }

    public void setLinkType(LinkType linkType) {
        this.linkType = linkType;
    }
}
