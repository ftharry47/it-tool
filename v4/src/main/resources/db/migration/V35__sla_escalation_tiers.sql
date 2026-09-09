-- Tiered SLA escalation: per-policy L1->L2->L3 tiers with dual triggers
-- (response/resolution breach OR stuck status), notify role + team reassignment.

CREATE TABLE IF NOT EXISTS sla_escalation_tier (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                UUID NOT NULL,
    sla_policy_id         UUID NOT NULL,
    level                 INT NOT NULL,
    trigger_type          VARCHAR(32) NOT NULL,  -- ON_RESPONSE_BREACH, ON_RESOLUTION_BREACH, ON_STUCK_STATUS
    stuck_status          VARCHAR(50) NULL,      -- incident status to match for ON_STUCK_STATUS
    stuck_minutes         INT NULL,              -- minutes in stuck_status before firing
    notify_role           VARCHAR(64) NULL,      -- role name whose members get notified
    reassign_to_team_id   UUID NULL,             -- team to reassign the incident to
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ NULL,
    CONSTRAINT fk_sla_tier_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_sla_tier_policy FOREIGN KEY (sla_policy_id) REFERENCES sla_policy(id),
    CONSTRAINT fk_sla_tier_team FOREIGN KEY (reassign_to_team_id) REFERENCES team(id),
    CONSTRAINT uq_sla_tier_policy_level UNIQUE (sla_policy_id, level)
);

CREATE INDEX IF NOT EXISTS idx_sla_tier_policy ON sla_escalation_tier(sla_policy_id);
CREATE INDEX IF NOT EXISTS idx_sla_tier_org ON sla_escalation_tier(org_id);

-- Track how far each SLA instance has escalated (0 = not escalated)
ALTER TABLE sla_instance
    ADD COLUMN IF NOT EXISTS escalation_level INT NOT NULL DEFAULT 0;

CREATE OR REPLACE TRIGGER trg_sla_escalation_tier_updated_at
BEFORE UPDATE ON sla_escalation_tier
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
