CREATE TABLE IF NOT EXISTS project (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL,
    key             VARCHAR(10) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    lead_id         UUID,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT uq_project_org_key UNIQUE (org_id, key),
    CONSTRAINT uq_project_org_name UNIQUE (org_id, name),
    CONSTRAINT fk_project_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_project_lead FOREIGN KEY (lead_id) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_project_org ON project(org_id);

CREATE TABLE IF NOT EXISTS issue_type (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL,
    name            VARCHAR(64) NOT NULL,
    description     TEXT,
    icon            VARCHAR(64),
    color           VARCHAR(32),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT uq_issue_type_org_name UNIQUE (org_id, name),
    CONSTRAINT fk_issue_type_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_issue_type_org ON issue_type(org_id);

CREATE TABLE IF NOT EXISTS workflow (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL,
    project_id      UUID,
    name            VARCHAR(128) NOT NULL,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT uq_workflow_org_name UNIQUE (org_id, name),
    CONSTRAINT fk_workflow_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_workflow_project FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE INDEX IF NOT EXISTS idx_workflow_org ON workflow(org_id);

CREATE TABLE IF NOT EXISTS workflow_status (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id     UUID NOT NULL,
    name            VARCHAR(64) NOT NULL,
    category        VARCHAR(32) NOT NULL,
    display_order   INT NOT NULL DEFAULT 0,
    is_terminal     BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT uq_workflow_status_workflow_name UNIQUE (workflow_id, name),
    CONSTRAINT fk_workflow_status_workflow FOREIGN KEY (workflow_id) REFERENCES workflow(id)
);

CREATE INDEX IF NOT EXISTS idx_workflow_status_workflow ON workflow_status(workflow_id);

CREATE TABLE IF NOT EXISTS workflow_transition (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id     UUID NOT NULL,
    from_status_id  UUID NOT NULL,
    to_status_id    UUID NOT NULL,
    screen          VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT uq_workflow_transition UNIQUE (workflow_id, from_status_id, to_status_id),
    CONSTRAINT fk_workflow_transition_workflow FOREIGN KEY (workflow_id) REFERENCES workflow(id),
    CONSTRAINT fk_workflow_transition_from FOREIGN KEY (from_status_id) REFERENCES workflow_status(id),
    CONSTRAINT fk_workflow_transition_to FOREIGN KEY (to_status_id) REFERENCES workflow_status(id)
);

CREATE INDEX IF NOT EXISTS idx_workflow_transition_workflow ON workflow_transition(workflow_id);
