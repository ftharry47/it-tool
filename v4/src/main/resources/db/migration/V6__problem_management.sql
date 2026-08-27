CREATE TABLE IF NOT EXISTS problem (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID NOT NULL,
    number        VARCHAR(20) NOT NULL,
    title         VARCHAR(500) NOT NULL,
    description   TEXT,
    status        VARCHAR(32) NOT NULL DEFAULT 'NEW',
    root_cause    TEXT,
    workaround    TEXT,
    assignee_id   UUID,
    resolved_at   TIMESTAMPTZ,
    closed_at     TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ NULL,
    CONSTRAINT uq_problem_number UNIQUE (org_id, number),
    CONSTRAINT fk_problem_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_problem_assignee FOREIGN KEY (assignee_id) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_problem_org ON problem(org_id);
CREATE INDEX IF NOT EXISTS idx_problem_status ON problem(status);

CREATE SEQUENCE IF NOT EXISTS problem_number_seq START 1;

CREATE TABLE IF NOT EXISTS problem_incident_link (
    problem_id   UUID NOT NULL,
    incident_id  UUID NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (problem_id, incident_id),
    CONSTRAINT fk_problem_link_problem FOREIGN KEY (problem_id) REFERENCES problem(id),
    CONSTRAINT fk_problem_link_incident FOREIGN KEY (incident_id) REFERENCES incident(id)
);

CREATE INDEX IF NOT EXISTS idx_problem_link_incident ON problem_incident_link(incident_id);
