package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

@Entity
@Table(name = "change_request")
public class ChangeRequest extends BaseEntity {

    public enum ChangeType { STANDARD, NORMAL, EMERGENCY }
    public enum Risk { LOW, MEDIUM, HIGH }
    public enum Status {
        DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, SCHEDULED, IN_PROGRESS, COMPLETED, FAILED, ROLLED_BACK, CANCELLED
    }

    @Column(name = "number", columnDefinition = "bigint not null default nextval('change_number_seq')", insertable = false, updatable = false, nullable = false)
    private Long number;

    @NotNull
    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "description", length = 4000)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", length = 32, nullable = false)
    private ChangeType changeType = ChangeType.NORMAL;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "risk", length = 32, nullable = false)
    private Risk risk = Risk.MEDIUM;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private Status status = Status.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private AppUser requestedBy;

    @Column(name = "planned_start")
    private OffsetDateTime plannedStart;

    @Column(name = "planned_end")
    private OffsetDateTime plannedEnd;

    @Column(name = "rollback_plan", length = 4000)
    private String rollbackPlan;

    @Column(name = "post_implementation_review", length = 4000)
    private String postImplementationReview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_problem_id")
    private Problem linkedProblem;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public void setChangeType(ChangeType changeType) {
        this.changeType = changeType;
    }

    public Risk getRisk() {
        return risk;
    }

    public void setRisk(Risk risk) {
        this.risk = risk;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public AppUser getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(AppUser requestedBy) {
        this.requestedBy = requestedBy;
    }

    public OffsetDateTime getPlannedStart() {
        return plannedStart;
    }

    public void setPlannedStart(OffsetDateTime plannedStart) {
        this.plannedStart = plannedStart;
    }

    public OffsetDateTime getPlannedEnd() {
        return plannedEnd;
    }

    public void setPlannedEnd(OffsetDateTime plannedEnd) {
        this.plannedEnd = plannedEnd;
    }

    public String getRollbackPlan() {
        return rollbackPlan;
    }

    public void setRollbackPlan(String rollbackPlan) {
        this.rollbackPlan = rollbackPlan;
    }

    public String getPostImplementationReview() {
        return postImplementationReview;
    }

    public void setPostImplementationReview(String postImplementationReview) {
        this.postImplementationReview = postImplementationReview;
    }

    public Problem getLinkedProblem() {
        return linkedProblem;
    }

    public void setLinkedProblem(Problem linkedProblem) {
        this.linkedProblem = linkedProblem;
    }
}
