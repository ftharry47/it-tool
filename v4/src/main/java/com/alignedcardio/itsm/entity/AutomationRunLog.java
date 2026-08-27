package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "automation_run_log")
public class AutomationRunLog extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "rule_id", nullable = false)
    private AutomationRule rule;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "triggered_event", nullable = false, length = 128)
    private String triggeredEvent;

    @Column(name = "payload", columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String payload;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "output", columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String output;

    @Column(name = "error", columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String error;

    @Column(name = "executed_at", nullable = false)
    private OffsetDateTime executedAt;

    public AutomationRule getRule() {
        return rule;
    }

    public void setRule(AutomationRule rule) {
        this.rule = rule;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getTriggeredEvent() {
        return triggeredEvent;
    }

    public void setTriggeredEvent(String triggeredEvent) {
        this.triggeredEvent = triggeredEvent;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public OffsetDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(OffsetDateTime executedAt) {
        this.executedAt = executedAt;
    }
}
