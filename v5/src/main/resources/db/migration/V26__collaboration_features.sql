CREATE TABLE IF NOT EXISTS time_entry (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id              UUID NOT NULL,
    entity_type         VARCHAR(50) NOT NULL,
    entity_id           UUID NOT NULL,
    user_id             UUID NOT NULL,
    time_spent_minutes  INT NOT NULL,
    description         TEXT,
    logged_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ NULL,
    CONSTRAINT fk_time_entry_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_time_entry_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_time_entry_entity ON time_entry(entity_type, entity_id);

ALTER TABLE incident ADD COLUMN IF NOT EXISTS estimated_minutes INT;
ALTER TABLE issue ADD COLUMN IF NOT EXISTS estimated_minutes INT;
