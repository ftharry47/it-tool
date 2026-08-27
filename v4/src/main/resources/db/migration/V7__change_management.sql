CREATE TABLE IF NOT EXISTS change_request (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                      UUID NOT NULL,
    number                      VARCHAR(20) NOT NULL,
    title                       VARCHAR(500) NOT NULL,
    description                 TEXT,
    change_type                 VARCHAR(32) NOT NULL,
    risk                        VARCHAR(32) NOT NULL,
    status                      VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    requested_by                UUID,
    planned_start               TIMESTAMPTZ,
    planned_end                 TIMESTAMPTZ,
    rollback_plan               TEXT,
    post_implementation_review  TEXT,
    linked_problem_id           UUID,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                  UUID,
    updated_by                  UUID,
    deleted_at                  TIMESTAMPTZ NULL,
    CONSTRAINT uq_change_number UNIQUE (org_id, number),
    CONSTRAINT fk_change_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_change_requested_by FOREIGN KEY (requested_by) REFERENCES app_user(id),
    CONSTRAINT fk_change_linked_problem FOREIGN KEY (linked_problem_id) REFERENCES problem(id)
);

CREATE INDEX IF NOT EXISTS idx_change_org ON change_request(org_id);
CREATE INDEX IF NOT EXISTS idx_change_status ON change_request(status);
CREATE INDEX IF NOT EXISTS idx_change_planned ON change_request(planned_start, planned_end);

CREATE SEQUENCE IF NOT EXISTS change_number_seq START 1;

CREATE TABLE IF NOT EXISTS change_approval (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    change_request_id   UUID NOT NULL,
    approver_id         UUID NOT NULL,
    sequence_order      INT NOT NULL DEFAULT 0,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    decided_at          TIMESTAMPTZ,
    comment             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ NULL,
    CONSTRAINT uq_change_approval UNIQUE (change_request_id, sequence_order),
    CONSTRAINT fk_change_approval_request FOREIGN KEY (change_request_id) REFERENCES change_request(id),
    CONSTRAINT fk_change_approval_approver FOREIGN KEY (approver_id) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_change_approval_request ON change_approval(change_request_id);
