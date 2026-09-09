package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "location")
public class Location extends BaseEntity {

    @NotNull
    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "address", length = 2000)
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_manager_user_id")
    private AppUser approvalManager;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public AppUser getApprovalManager() {
        return approvalManager;
    }

    public void setApprovalManager(AppUser approvalManager) {
        this.approvalManager = approvalManager;
    }
}
