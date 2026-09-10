package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Entity
@Table(name = "sla_escalation_tier")
public class SlaEscalationTier extends BaseEntity {

    public enum TriggerType { ON_RESPONSE_BREACH, ON_RESOLUTION_BREACH, ON_STUCK_STATUS }

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sla_policy_id", nullable = false)
    private SlaPolicy policy;

    @Column(name = "level", nullable = false)
    private int level;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 32, nullable = false)
    private TriggerType triggerType;

    @Column(name = "stuck_status", length = 50)
    private String stuckStatus;

    @Column(name = "stuck_minutes")
    private Integer stuckMinutes;

    @Column(name = "notify_role", length = 64)
    private String notifyRole;

    @Column(name = "reassign_to_team_id")
    private UUID reassignToTeamId;

    public SlaPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(SlaPolicy policy) {
        this.policy = policy;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(TriggerType triggerType) {
        this.triggerType = triggerType;
    }

    public String getStuckStatus() {
        return stuckStatus;
    }

    public void setStuckStatus(String stuckStatus) {
        this.stuckStatus = stuckStatus;
    }

    public Integer getStuckMinutes() {
        return stuckMinutes;
    }

    public void setStuckMinutes(Integer stuckMinutes) {
        this.stuckMinutes = stuckMinutes;
    }

    public String getNotifyRole() {
        return notifyRole;
    }

    public void setNotifyRole(String notifyRole) {
        this.notifyRole = notifyRole;
    }

    public UUID getReassignToTeamId() {
        return reassignToTeamId;
    }

    public void setReassignToTeamId(UUID reassignToTeamId) {
        this.reassignToTeamId = reassignToTeamId;
    }
}
