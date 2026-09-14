package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sla_instance")
public class SlaInstance extends BaseEntity {

    public enum BreachStatus { ON_TRACK, AT_RISK, BREACHED }

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sla_policy_id", nullable = false)
    private SlaPolicy policy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", unique = true)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_request_id", unique = true)
    private ServiceRequest serviceRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", unique = true)
    private Problem problem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_request_id", unique = true)
    private ChangeRequest changeRequest;

    @Column(name = "response_due_at")
    private OffsetDateTime responseDueAt;

    @Column(name = "resolution_due_at")
    private OffsetDateTime resolutionDueAt;

    @Column(name = "response_met_at")
    private OffsetDateTime responseMetAt;

    @Column(name = "resolution_met_at")
    private OffsetDateTime resolutionMetAt;

    @Column(name = "paused_at")
    private OffsetDateTime pausedAt;

    @Column(name = "total_paused_minutes", nullable = false)
    private int totalPausedMinutes = 0;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "breach_status", length = 32, nullable = false)
    private BreachStatus breachStatus = BreachStatus.ON_TRACK;

    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel = 0;

    public SlaPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(SlaPolicy policy) {
        this.policy = policy;
    }

    public Incident getIncident() {
        return incident;
    }

    public void setIncident(Incident incident) {
        this.incident = incident;
    }

    public ServiceRequest getServiceRequest() {
        return serviceRequest;
    }

    public void setServiceRequest(ServiceRequest serviceRequest) {
        this.serviceRequest = serviceRequest;
    }

    public Problem getProblem() {
        return problem;
    }

    public void setProblem(Problem problem) {
        this.problem = problem;
    }

    public ChangeRequest getChangeRequest() {
        return changeRequest;
    }

    public void setChangeRequest(ChangeRequest changeRequest) {
        this.changeRequest = changeRequest;
    }

    public OffsetDateTime getResponseDueAt() {
        return responseDueAt;
    }

    public void setResponseDueAt(OffsetDateTime responseDueAt) {
        this.responseDueAt = responseDueAt;
    }

    public OffsetDateTime getResolutionDueAt() {
        return resolutionDueAt;
    }

    public void setResolutionDueAt(OffsetDateTime resolutionDueAt) {
        this.resolutionDueAt = resolutionDueAt;
    }

    public OffsetDateTime getResponseMetAt() {
        return responseMetAt;
    }

    public void setResponseMetAt(OffsetDateTime responseMetAt) {
        this.responseMetAt = responseMetAt;
    }

    public OffsetDateTime getResolutionMetAt() {
        return resolutionMetAt;
    }

    public void setResolutionMetAt(OffsetDateTime resolutionMetAt) {
        this.resolutionMetAt = resolutionMetAt;
    }

    public OffsetDateTime getPausedAt() {
        return pausedAt;
    }

    public void setPausedAt(OffsetDateTime pausedAt) {
        this.pausedAt = pausedAt;
    }

    public int getTotalPausedMinutes() {
        return totalPausedMinutes;
    }

    public void setTotalPausedMinutes(int totalPausedMinutes) {
        this.totalPausedMinutes = totalPausedMinutes;
    }

    public BreachStatus getBreachStatus() {
        return breachStatus;
    }

    public void setBreachStatus(BreachStatus breachStatus) {
        this.breachStatus = breachStatus;
    }

    public int getEscalationLevel() {
        return escalationLevel;
    }

    public void setEscalationLevel(int escalationLevel) {
        this.escalationLevel = escalationLevel;
    }
}
