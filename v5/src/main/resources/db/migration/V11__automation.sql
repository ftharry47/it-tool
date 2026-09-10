CREATE TABLE IF NOT EXISTS automation_rule (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    trigger_type    VARCHAR(64) NOT NULL,
    trigger_entity  VARCHAR(64) NOT NULL,
    trigger_config  TEXT NOT NULL DEFAULT '{}',
    conditions      TEXT NOT NULL DEFAULT '{}',
    actions         TEXT NOT NULL DEFAULT '{}',
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT fk_automation_rule_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_automation_rule_org ON automation_rule(org_id);
CREATE INDEX IF NOT EXISTS idx_automation_rule_trigger ON automation_rule(org_id, trigger_entity, trigger_type, active);

CREATE TABLE IF NOT EXISTS automation_run_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL,
    rule_id         UUID NOT NULL,
    entity_type     VARCHAR(64) NOT NULL,
    entity_id       UUID NOT NULL,
    triggered_event VARCHAR(128) NOT NULL,
    payload         TEXT,
    status          VARCHAR(32) NOT NULL,
    output          TEXT,
    error           TEXT,
    executed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_automation_run_log_rule FOREIGN KEY (rule_id) REFERENCES automation_rule(id),
    CONSTRAINT fk_automation_run_log_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_automation_run_log_rule ON automation_run_log(rule_id);
CREATE INDEX IF NOT EXISTS idx_automation_run_log_org ON automation_run_log(org_id);

-- updated_at triggers
CREATE OR REPLACE TRIGGER trg_automation_rule_updated_at
BEFORE UPDATE ON automation_rule
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
