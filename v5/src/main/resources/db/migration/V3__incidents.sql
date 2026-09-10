CREATE SEQUENCE IF NOT EXISTS incident_number_seq START 1000;

CREATE TABLE IF NOT EXISTS priority (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID NOT NULL,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    display_order INT NOT NULL DEFAULT 0,
    status        VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ NULL,
    CONSTRAINT fk_priority_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT uq_priority_org_name UNIQUE (org_id, name)
);

CREATE INDEX IF NOT EXISTS idx_priority_name ON priority(name);

CREATE TABLE IF NOT EXISTS category (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID NOT NULL,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    display_order INT NOT NULL DEFAULT 0,
    status        VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ NULL,
    CONSTRAINT fk_category_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT uq_category_org_name UNIQUE (org_id, name)
);

CREATE INDEX IF NOT EXISTS idx_category_name ON category(name);

CREATE TABLE IF NOT EXISTS incident (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID NOT NULL,
    number        BIGINT NOT NULL DEFAULT nextval('incident_number_seq'),
    requester_id  UUID NOT NULL,
    assignee_id   UUID,
    title         VARCHAR(500) NOT NULL,
    description   TEXT,
    impact        INT NOT NULL DEFAULT 3,
    urgency       INT NOT NULL DEFAULT 3,
    status        VARCHAR(50) NOT NULL DEFAULT 'NEW',
    priority_id   UUID,
    category_id   UUID,
    resolved_at   TIMESTAMPTZ,
    closed_at     TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ NULL,
    CONSTRAINT fk_incident_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_incident_requester FOREIGN KEY (requester_id) REFERENCES app_user(id),
    CONSTRAINT fk_incident_assignee FOREIGN KEY (assignee_id) REFERENCES app_user(id),
    CONSTRAINT fk_incident_priority FOREIGN KEY (priority_id) REFERENCES priority(id),
    CONSTRAINT fk_incident_category FOREIGN KEY (category_id) REFERENCES category(id),
    CONSTRAINT uq_incident_org_number UNIQUE (org_id, number)
);

CREATE INDEX IF NOT EXISTS idx_incident_status ON incident(status);
CREATE INDEX IF NOT EXISTS idx_incident_assignee ON incident(assignee_id);
CREATE INDEX IF NOT EXISTS idx_incident_requester ON incident(requester_id);

CREATE TABLE IF NOT EXISTS incident_comment (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID NOT NULL,
    incident_id   UUID NOT NULL,
    author_id     UUID NOT NULL,
    body          TEXT NOT NULL,
    is_public     BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ NULL,
    CONSTRAINT fk_incident_comment_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_incident_comment_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
    CONSTRAINT fk_incident_comment_author FOREIGN KEY (author_id) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_incident_comment_incident ON incident_comment(incident_id);

-- updated_at triggers
CREATE OR REPLACE TRIGGER trg_priority_updated_at
BEFORE UPDATE ON priority
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE TRIGGER trg_category_updated_at
BEFORE UPDATE ON category
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE TRIGGER trg_incident_updated_at
BEFORE UPDATE ON incident
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE TRIGGER trg_incident_comment_updated_at
BEFORE UPDATE ON incident_comment
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- seed default priorities
INSERT INTO priority (id, org_id, name, description, display_order, status)
VALUES
    ('00000000-0000-0000-0000-000000000200', '00000000-0000-0000-0000-000000000001', 'Critical', 'Critical impact', 1, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000001', 'High', 'High impact', 2, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000001', 'Medium', 'Medium impact', 3, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000203', '00000000-0000-0000-0000-000000000001', 'Low', 'Low impact', 4, 'ACTIVE')
ON CONFLICT ON CONSTRAINT uq_priority_org_name DO NOTHING;

-- seed default categories
INSERT INTO category (id, org_id, name, description, display_order, status)
VALUES
    ('00000000-0000-0000-0000-000000000300', '00000000-0000-0000-0000-000000000001', 'Software', 'Software issue', 1, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000001', 'Hardware', 'Hardware issue', 2, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000001', 'Network', 'Network issue', 3, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000001', 'Email', 'Email issue', 4, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000304', '00000000-0000-0000-0000-000000000001', 'Access', 'Access request', 5, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000305', '00000000-0000-0000-0000-000000000001', 'Password', 'Password reset', 6, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000306', '00000000-0000-0000-0000-000000000001', 'Printer', 'Printer issue', 7, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000307', '00000000-0000-0000-0000-000000000001', 'Mobile', 'Mobile device', 8, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000308', '00000000-0000-0000-0000-000000000001', 'Knowledge Base', 'Knowledge base request', 9, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000309', '00000000-0000-0000-0000-000000000001', 'Service Desk', 'General service desk', 10, 'ACTIVE')
ON CONFLICT ON CONSTRAINT uq_category_org_name DO NOTHING;
