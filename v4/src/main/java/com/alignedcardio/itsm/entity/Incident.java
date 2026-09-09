package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "incident")
public class Incident extends BaseEntity {

    public enum Status {
        NEW, IN_PROGRESS, ON_HOLD, WAITING_ON_CUSTOMER, RESOLVED, CLOSED, REOPENED
    }

    @Column(name = "number", columnDefinition = "bigint not null default nextval('incident_number_seq')", insertable = false, updatable = false, nullable = false)
    private Long number;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private AppUser requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private AppUser assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_team_id")
    private Team assignmentTeam;

    @NotNull
    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "impact", nullable = false)
    private int impact = 3;

    @Column(name = "urgency", nullable = false)
    private int urgency = 3;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private Status status = Status.NEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "priority_id")
    private Priority priority;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "resolved_at", columnDefinition = "timestamptz")
    private OffsetDateTime resolvedAt;

    @Column(name = "closed_at", columnDefinition = "timestamptz")
    private OffsetDateTime closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    // One-time estimate lock: set when the current assignee records an
    // estimate; cleared on reassignment, tier escalation, or reopen so the
    // new owner gets exactly one fresh estimate opportunity.
    @Column(name = "estimate_set_at", columnDefinition = "timestamptz")
    private OffsetDateTime estimateSetAt;

    @Column(name = "estimate_set_by_id")
    private UUID estimateSetById;

    // Staff-only notes recorded when the incident is closed (AGENT+ visibility).
    @Column(name = "closing_notes", columnDefinition = "text")
    private String closingNotes;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<IncidentComment> comments = new ArrayList<>();

    public Long getNumber() {
        return number;
    }

    public void setNumber(Long number) {
        this.number = number;
    }

    public AppUser getRequester() {
        return requester;
    }

    public void setRequester(AppUser requester) {
        this.requester = requester;
    }

    public AppUser getAssignee() {
        return assignee;
    }

    public void setAssignee(AppUser assignee) {
        this.assignee = assignee;
    }

    public Team getAssignmentTeam() {
        return assignmentTeam;
    }

    public void setAssignmentTeam(Team assignmentTeam) {
        this.assignmentTeam = assignmentTeam;
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

    public int getImpact() {
        return impact;
    }

    public void setImpact(int impact) {
        this.impact = impact;
    }

    public int getUrgency() {
        return urgency;
    }

    public void setUrgency(int urgency) {
        this.urgency = urgency;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(OffsetDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public List<IncidentComment> getComments() {
        return comments;
    }

    public void setComments(List<IncidentComment> comments) {
        this.comments = comments;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(Integer estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }

    public OffsetDateTime getEstimateSetAt() {
        return estimateSetAt;
    }

    public void setEstimateSetAt(OffsetDateTime estimateSetAt) {
        this.estimateSetAt = estimateSetAt;
    }

    public UUID getEstimateSetById() {
        return estimateSetById;
    }

    public void setEstimateSetById(UUID estimateSetById) {
        this.estimateSetById = estimateSetById;
    }

    public String getClosingNotes() {
        return closingNotes;
    }

    public void setClosingNotes(String closingNotes) {
        this.closingNotes = closingNotes;
    }
}
