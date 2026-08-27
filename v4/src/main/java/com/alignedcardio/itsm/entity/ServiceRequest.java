package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

@Entity
@Table(name = "service_request")
public class ServiceRequest extends BaseEntity {

    public enum Status {
        SUBMITTED, PENDING_APPROVAL, APPROVED, REJECTED, IN_FULFILLMENT, FULFILLED, CANCELLED
    }

    public enum ApprovalDecision {
        PENDING, APPROVED, REJECTED
    }

    @Column(name = "number", columnDefinition = "bigint not null default nextval('service_request_number_seq')", insertable = false, updatable = false, nullable = false)
    private Long number;

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
    private String formData;

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

    @Column(name = "needed_by")
    private OffsetDateTime neededBy;

    public Long getNumber() {
        return number;
    }

    public void setNumber(Long number) {
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

    public String getFormData() {
        return formData;
    }

    public void setFormData(String formData) {
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

    public OffsetDateTime getNeededBy() {
        return neededBy;
    }

    public void setNeededBy(OffsetDateTime neededBy) {
        this.neededBy = neededBy;
    }
}
