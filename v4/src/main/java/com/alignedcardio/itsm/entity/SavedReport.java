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
}
