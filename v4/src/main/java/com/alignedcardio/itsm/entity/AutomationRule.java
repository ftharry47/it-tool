package com.alignedcardio.itsm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "automation_rule")
public class AutomationRule extends BaseEntity {

    public enum TriggerType {
        CREATED,
        UPDATED,
        STATUS_CHANGED,
        SPRINT_STARTED,
        SPRINT_COMPLETED
    }

    public enum TriggerEntity {
        INCIDENT,
        PROBLEM,
        CHANGE,
        REQUEST,
        ISSUE
    }

    private String name;

    private String description;

    @Column(name = "trigger_type", nullable = false, length = 64)
    private String triggerType;

    @Column(name = "trigger_entity", nullable = false, length = 64)
    private String triggerEntity;

    @Column(name = "trigger_config", nullable = false, columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String triggerConfig;

    @Column(name = "conditions", nullable = false, columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String conditions;

    @Column(name = "actions", nullable = false, columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String actions;

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

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getTriggerEntity() {
        return triggerEntity;
    }

    public void setTriggerEntity(String triggerEntity) {
        this.triggerEntity = triggerEntity;
    }

    public String getTriggerConfig() {
        return triggerConfig;
    }

    public void setTriggerConfig(String triggerConfig) {
        this.triggerConfig = triggerConfig;
    }

    public String getConditions() {
        return conditions;
    }

    public void setConditions(String conditions) {
        this.conditions = conditions;
    }

    public String getActions() {
        return actions;
    }

    public void setActions(String actions) {
        this.actions = actions;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
