CREATE TABLE IF NOT EXISTS sprint (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL,
    project_id      UUID NOT NULL,
    name            VARCHAR(128) NOT NULL,
    goal            TEXT,
    status          VARCHAR(32) NOT NULL DEFAULT 'PLANNING',
    start_date      TIMESTAMPTZ,
    end_date        TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT uq_sprint_project_name UNIQUE (project_id, name),
    CONSTRAINT fk_sprint_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_sprint_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_sprint_project ON sprint(project_id);

CREATE TABLE IF NOT EXISTS issue (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id              UUID NOT NULL,
    project_id          UUID NOT NULL,
    issue_type_id       UUID,
    workflow_id         UUID NOT NULL,
    workflow_status_id  UUID NOT NULL,
    sprint_id           UUID,
    epic_id             UUID,
    parent_issue_id     UUID,
    number              INT NOT NULL,
    key                 VARCHAR(32) NOT NULL,
    summary             VARCHAR(255) NOT NULL,
    description         TEXT,
    assignee_id         UUID,
    reporter_id         UUID NOT NULL,
    story_points        INT,
    remaining_points    INT,
    priority            VARCHAR(32) DEFAULT 'MEDIUM',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT uq_issue_project_number UNIQUE (project_id, number),
    CONSTRAINT uq_issue_project_key UNIQUE (project_id, key),
    CONSTRAINT fk_issue_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_issue_issue_type FOREIGN KEY (issue_type_id) REFERENCES issue_type(id),
    CONSTRAINT fk_issue_workflow FOREIGN KEY (workflow_id) REFERENCES workflow(id),
    CONSTRAINT fk_issue_workflow_status FOREIGN KEY (workflow_status_id) REFERENCES workflow_status(id),
    CONSTRAINT fk_issue_sprint FOREIGN KEY (sprint_id) REFERENCES sprint(id),
    CONSTRAINT fk_issue_epic FOREIGN KEY (epic_id) REFERENCES issue(id),
    CONSTRAINT fk_issue_parent FOREIGN KEY (parent_issue_id) REFERENCES issue(id),
    CONSTRAINT fk_issue_assignee FOREIGN KEY (assignee_id) REFERENCES app_user(id),
    CONSTRAINT fk_issue_reporter FOREIGN KEY (reporter_id) REFERENCES app_user(id),
    CONSTRAINT fk_issue_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_issue_project ON issue(project_id);
CREATE INDEX IF NOT EXISTS idx_issue_sprint ON issue(sprint_id);
CREATE INDEX IF NOT EXISTS idx_issue_status ON issue(workflow_status_id);
CREATE INDEX IF NOT EXISTS idx_issue_assignee ON issue(assignee_id);

CREATE SEQUENCE IF NOT EXISTS issue_number_seq START 1;

CREATE TABLE IF NOT EXISTS issue_link (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL,
    from_issue_id   UUID NOT NULL,
    to_issue_id     UUID NOT NULL,
    link_type       VARCHAR(32) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    CONSTRAINT uq_issue_link UNIQUE (from_issue_id, to_issue_id, link_type),
    CONSTRAINT fk_issue_link_from FOREIGN KEY (from_issue_id) REFERENCES issue(id),
    CONSTRAINT fk_issue_link_to FOREIGN KEY (to_issue_id) REFERENCES issue(id),
    CONSTRAINT fk_issue_link_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_issue_link_from ON issue_link(from_issue_id);
CREATE INDEX IF NOT EXISTS idx_issue_link_to ON issue_link(to_issue_id);

CREATE TABLE IF NOT EXISTS issue_comment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL,
    issue_id        UUID NOT NULL,
    body            TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT fk_issue_comment_issue FOREIGN KEY (issue_id) REFERENCES issue(id),
    CONSTRAINT fk_issue_comment_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_issue_comment_issue ON issue_comment(issue_id);

CREATE TABLE IF NOT EXISTS sprint_burndown_snapshot (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL,
    sprint_id       UUID NOT NULL,
    snapshot_date   TIMESTAMPTZ NOT NULL,
    total_points    INT NOT NULL DEFAULT 0,
    remaining_points INT NOT NULL DEFAULT 0,
    open_issues     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_burndown_sprint_date UNIQUE (sprint_id, snapshot_date),
    CONSTRAINT fk_burndown_sprint FOREIGN KEY (sprint_id) REFERENCES sprint(id),
    CONSTRAINT fk_burndown_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_burndown_sprint ON sprint_burndown_snapshot(sprint_id);
