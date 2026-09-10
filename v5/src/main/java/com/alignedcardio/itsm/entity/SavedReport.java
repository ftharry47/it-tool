package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "saved_report")
public class SavedReport extends BaseEntity {

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "entity", length = 64, nullable = false)
    private String entity;

    @Column(name = "filters", columnDefinition = "text")
    private String filters;

    @Column(name = "group_by", length = 64)
    private String groupBy;

    @Column(name = "date_range", columnDefinition = "text")
    private String dateRange;

    /** 'AD_HOC' (user-saved query) or 'AGENT_PERFORMANCE' (auto-generated monthly). */
    @Column(name = "report_type", length = 32, nullable = false)
    private String reportType = "AD_HOC";

    /** For AGENT_PERFORMANCE reports: the agent this report belongs to. */
    @Column(name = "owner_user_id")
    private java.util.UUID ownerUserId;

    /** Frozen report content (metrics + score) as JSON for generated reports. */
    @Column(name = "payload", columnDefinition = "text")
    private String payload;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public String getFilters() {
        return filters;
    }

    public void setFilters(String filters) {
        this.filters = filters;
    }

    public String getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(String groupBy) {
        this.groupBy = groupBy;
    }

    public String getDateRange() {
        return dateRange;
    }

    public void setDateRange(String dateRange) {
        this.dateRange = dateRange;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public java.util.UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(java.util.UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
