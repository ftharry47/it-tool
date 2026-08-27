CREATE TABLE IF NOT EXISTS app_user (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID NOT NULL,
    object_id     VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    display_name  VARCHAR(255),
    job_title     VARCHAR(255),
    department    VARCHAR(255),
    status        VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ NULL,
    CONSTRAINT fk_app_user_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT uq_app_user_org_object UNIQUE (org_id, object_id),
    CONSTRAINT uq_app_user_org_email UNIQUE (org_id, email)
);

CREATE INDEX IF NOT EXISTS idx_app_user_email ON app_user(email);
CREATE INDEX IF NOT EXISTS idx_app_user_object_id ON app_user(object_id);

CREATE TABLE IF NOT EXISTS role (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID NOT NULL,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    status        VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ NULL,
    CONSTRAINT fk_role_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT uq_role_org_name UNIQUE (org_id, name)
);

CREATE INDEX IF NOT EXISTS idx_role_name ON role(name);

CREATE TABLE IF NOT EXISTS user_role (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID NOT NULL,
    user_id       UUID NOT NULL,
    role_id       UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ NULL,
    CONSTRAINT fk_user_role_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role(id),
    CONSTRAINT uq_user_role UNIQUE (org_id, user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_user_role_user_id ON user_role(user_id);
CREATE INDEX IF NOT EXISTS idx_user_role_role_id ON user_role(role_id);

-- Apply the base updated_at trigger to all identity tables
CREATE OR REPLACE TRIGGER trg_app_user_updated_at
BEFORE UPDATE ON app_user
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE TRIGGER trg_role_updated_at
BEFORE UPDATE ON role
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE TRIGGER trg_user_role_updated_at
BEFORE UPDATE ON user_role
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- Seed the default org roles
INSERT INTO role (id, org_id, name, description, status)
VALUES
    ('00000000-0000-0000-0000-000000000100', '00000000-0000-0000-0000-000000000001', 'END_USER', 'Can submit and track own tickets', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001', 'AGENT', 'Can view and work assigned tickets', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000001', 'TEAM_LEAD', 'Can manage queue and team assignments', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000001', 'ADMIN', 'Can manage users, SLAs, and catalog', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-000000000001', 'SUPER_ADMIN', 'Full system access across all orgs', 'ACTIVE')
ON CONFLICT ON CONSTRAINT uq_role_org_name DO NOTHING;
