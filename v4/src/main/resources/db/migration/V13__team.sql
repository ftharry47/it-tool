CREATE TABLE IF NOT EXISTS team (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID NOT NULL,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ NULL,
    CONSTRAINT fk_team_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT uq_team_org_name UNIQUE (org_id, name)
);

CREATE INDEX IF NOT EXISTS idx_team_org_id ON team(org_id);

CREATE TABLE IF NOT EXISTS team_member (
    team_id       UUID NOT NULL,
    user_id       UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    CONSTRAINT pk_team_member PRIMARY KEY (team_id, user_id),
    CONSTRAINT fk_team_member_team FOREIGN KEY (team_id) REFERENCES team(id),
    CONSTRAINT fk_team_member_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_team_member_user ON team_member(user_id);

CREATE OR REPLACE TRIGGER trg_team_updated_at
BEFORE UPDATE ON team
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
