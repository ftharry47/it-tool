package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "problem_incident_link")
@IdClass(ProblemIncidentLink.ProblemIncidentLinkId.class)
public class ProblemIncidentLink {

    @Id
    @Column(name = "problem_id", nullable = false)
    private UUID problemId;

    @Id
    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getProblemId() {
        return problemId;
    }

    public void setProblemId(UUID problemId) {
        this.problemId = problemId;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(UUID incidentId) {
        this.incidentId = incidentId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public static class ProblemIncidentLinkId implements Serializable {
        private UUID problemId;
        private UUID incidentId;

        public ProblemIncidentLinkId() {
        }

        public ProblemIncidentLinkId(UUID problemId, UUID incidentId) {
            this.problemId = problemId;
            this.incidentId = incidentId;
        }

        public UUID getProblemId() {
            return problemId;
        }

        public void setProblemId(UUID problemId) {
            this.problemId = problemId;
        }

        public UUID getIncidentId() {
            return incidentId;
        }

        public void setIncidentId(UUID incidentId) {
            this.incidentId = incidentId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProblemIncidentLinkId that = (ProblemIncidentLinkId) o;
            return java.util.Objects.equals(problemId, that.problemId) &&
                    java.util.Objects.equals(incidentId, that.incidentId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(problemId, incidentId);
        }
    }
}
