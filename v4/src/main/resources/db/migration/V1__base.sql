CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Default organization for single-tenant deployment
CREATE TABLE IF NOT EXISTS org (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ NULL
);

INSERT INTO org (id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'Default Organization')
ON CONFLICT (id) DO NOTHING;

-- Auto-update trigger for updated_at
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply the updated_at trigger to the org table as the model for all future tables
CREATE OR REPLACE TRIGGER trg_org_updated_at
BEFORE UPDATE ON org
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
