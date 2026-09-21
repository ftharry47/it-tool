package com.alignedcardio.itsm.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "service_request")
public class ServiceRequest extends BaseEntity {

    public enum Status {
        SUBMITTED, PENDING_APPROVAL, APPROVED, REJECTED, REJECTED_NEEDS_REVIEW,
        IN_FULFILLMENT, ON_HOLD, FULFILLED, CANCELLED
    }

    public enum ApprovalDecision {
        PENDING, APPROVED, REJECTED
    }

    @Column(name = "number", nullable = false)
    private String number;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalog_item_id", nullable = false)
    private CatalogItem catalogItem;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private AppUser requester;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private Status status = Status.SUBMITTED;

    @NotNull
    @Column(name = "form_data", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode formData;

    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private AppUser approver;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_decision", length = 32)
    private ApprovalDecision approvalDecision;

    @Column(name = "approval_comment", length = 2000)
    private String approvalComment;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 32)
    private Status previousStatus;

    @Column(name = "needed_by")
    private OffsetDateTime neededBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "phone", length = 20)
    private String phone;

    /** Optional SLA-driving priority (matches REQUEST policy priorityFilter). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "priority_id")
    private Priority priority;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public CatalogItem getCatalogItem() {
        return catalogItem;
    }

    public void setCatalogItem(CatalogItem catalogItem) {
        this.catalogItem = catalogItem;
    }

    public AppUser getRequester() {
        return requester;
    }

    public void setRequester(AppUser requester) {
        this.requester = requester;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public JsonNode getFormData() {
        return formData;
    }

    public void setFormData(JsonNode formData) {
        this.formData = formData;
    }

    public boolean isApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
    }

    public AppUser getApprover() {
        return approver;
    }

    public void setApprover(AppUser approver) {
        this.approver = approver;
    }

    public ApprovalDecision getApprovalDecision() {
        return approvalDecision;
    }

    public void setApprovalDecision(ApprovalDecision approvalDecision) {
        this.approvalDecision = approvalDecision;
    }

    public String getApprovalComment() {
        return approvalComment;
    }

    public void setApprovalComment(String approvalComment) {
        this.approvalComment = approvalComment;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(OffsetDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public Status getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(Status previousStatus) {
        this.previousStatus = previousStatus;
    }

    public OffsetDateTime getNeededBy() {
        return neededBy;
    }

    public void setNeededBy(OffsetDateTime neededBy) {
        this.neededBy = neededBy;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }
}
