package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "catalog_item")
public class CatalogItem extends BaseEntity {

    @NotNull
    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "category", length = 100)
    private String category;

    @NotNull
    @Column(name = "form_schema", columnDefinition = "jsonb", nullable = false)
    private String formSchema;

    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private AppUser approver;

    @Column(name = "fulfillment_tasks", columnDefinition = "jsonb")
    private String fulfillmentTasks;

    @Column(name = "active", nullable = false)
    private boolean active = true;

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getFormSchema() {
        return formSchema;
    }

    public void setFormSchema(String formSchema) {
        this.formSchema = formSchema;
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

    public String getFulfillmentTasks() {
        return fulfillmentTasks;
    }

    public void setFulfillmentTasks(String fulfillmentTasks) {
        this.fulfillmentTasks = fulfillmentTasks;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
