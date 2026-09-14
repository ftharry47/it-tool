package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "sla_policy")
public class SlaPolicy extends BaseEntity {

    public enum AppliesTo { INCIDENT, REQUEST, PROBLEM, CHANGE }

    @NotNull
    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", length = 32, nullable = false)
    private AppliesTo appliesTo = AppliesTo.INCIDENT;

    @Column(name = "priority_filter", length = 32)
    private String priorityFilter;

    @Column(name = "response_target_minutes", nullable = false)
    private int responseTargetMinutes = 60;

    @Column(name = "resolution_target_minutes", nullable = false)
    private int resolutionTargetMinutes = 480;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_hours_calendar_id")
    private BusinessCalendar businessHoursCalendar;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AppliesTo getAppliesTo() {
        return appliesTo;
    }

    public void setAppliesTo(AppliesTo appliesTo) {
        this.appliesTo = appliesTo;
    }

    public String getPriorityFilter() {
        return priorityFilter;
    }

    public void setPriorityFilter(String priorityFilter) {
        this.priorityFilter = priorityFilter;
    }

    public int getResponseTargetMinutes() {
        return responseTargetMinutes;
    }

    public void setResponseTargetMinutes(int responseTargetMinutes) {
        this.responseTargetMinutes = responseTargetMinutes;
    }

    public int getResolutionTargetMinutes() {
        return resolutionTargetMinutes;
    }

    public void setResolutionTargetMinutes(int resolutionTargetMinutes) {
        this.resolutionTargetMinutes = resolutionTargetMinutes;
    }

    public BusinessCalendar getBusinessHoursCalendar() {
        return businessHoursCalendar;
    }

    public void setBusinessHoursCalendar(BusinessCalendar businessHoursCalendar) {
        this.businessHoursCalendar = businessHoursCalendar;
    }
}
