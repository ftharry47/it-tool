package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "fulfillment_task")
public class FulfillmentTask extends BaseEntity {

    public enum Status { PENDING, ORDERED, DELIVERY_DATE_SET, DELIVERED, COMPLETED }

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest serviceRequest;

    @NotNull
    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "sequence_order", nullable = false)
    private int sequenceOrder = 0;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private Status status = Status.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private AppUser assignee;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_id")
    private AppUser assignedBy;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "last_reminded_on")
    private LocalDate lastRemindedOn;

    @Column(name = "closing_notes", columnDefinition = "TEXT")
    private String closingNotes;

    public ServiceRequest getServiceRequest() {
        return serviceRequest;
    }

    public void setServiceRequest(ServiceRequest serviceRequest) {
        this.serviceRequest = serviceRequest;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getSequenceOrder() {
        return sequenceOrder;
    }

    public void setSequenceOrder(int sequenceOrder) {
        this.sequenceOrder = sequenceOrder;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public AppUser getAssignee() {
        return assignee;
    }

    public void setAssignee(AppUser assignee) {
        this.assignee = assignee;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(OffsetDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }

    public AppUser getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(AppUser assignedBy) {
        this.assignedBy = assignedBy;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDate getExpectedDeliveryDate() {
        return expectedDeliveryDate;
    }

    public void setExpectedDeliveryDate(LocalDate expectedDeliveryDate) {
        this.expectedDeliveryDate = expectedDeliveryDate;
    }

    public OffsetDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(OffsetDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public LocalDate getLastRemindedOn() {
        return lastRemindedOn;
    }

    public void setLastRemindedOn(LocalDate lastRemindedOn) {
        this.lastRemindedOn = lastRemindedOn;
    }

    public String getClosingNotes() {
        return closingNotes;
    }

    public void setClosingNotes(String closingNotes) {
        this.closingNotes = closingNotes;
    }
}
