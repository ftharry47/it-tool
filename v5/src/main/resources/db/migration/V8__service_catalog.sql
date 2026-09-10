CREATE TABLE IF NOT EXISTS catalog_item (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id              UUID NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    category            VARCHAR(100),
    form_schema         JSONB NOT NULL,
    approval_required   BOOLEAN NOT NULL DEFAULT FALSE,
    approver_id         UUID,
    fulfillment_tasks   JSONB,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ NULL,
    CONSTRAINT fk_catalog_item_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_catalog_item_approver FOREIGN KEY (approver_id) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_catalog_item_org ON catalog_item(org_id);
CREATE INDEX IF NOT EXISTS idx_catalog_item_active ON catalog_item(active);

CREATE TABLE IF NOT EXISTS service_request (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                  UUID NOT NULL,
    number                  VARCHAR(20) NOT NULL,
    catalog_item_id         UUID NOT NULL,
    requester_id            UUID NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    form_data               JSONB NOT NULL,
    approval_required       BOOLEAN NOT NULL DEFAULT FALSE,
    approver_id             UUID,
    approval_decision       VARCHAR(32),
    approval_comment        TEXT,
    decided_at              TIMESTAMPTZ,
    needed_by               TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              UUID,
    updated_by              UUID,
    deleted_at              TIMESTAMPTZ NULL,
    CONSTRAINT uq_service_request_number UNIQUE (org_id, number),
    CONSTRAINT fk_service_request_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_service_request_catalog_item FOREIGN KEY (catalog_item_id) REFERENCES catalog_item(id),
    CONSTRAINT fk_service_request_requester FOREIGN KEY (requester_id) REFERENCES app_user(id),
    CONSTRAINT fk_service_request_approver FOREIGN KEY (approver_id) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_service_request_org ON service_request(org_id);
CREATE INDEX IF NOT EXISTS idx_service_request_status ON service_request(status);
CREATE INDEX IF NOT EXISTS idx_service_request_catalog ON service_request(catalog_item_id);

CREATE SEQUENCE IF NOT EXISTS service_request_number_seq START 1;

CREATE TABLE IF NOT EXISTS fulfillment_task (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_request_id  UUID NOT NULL,
    description         TEXT NOT NULL,
    sequence_order      INT NOT NULL DEFAULT 0,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    assignee_id         UUID,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ NULL,
    CONSTRAINT fk_fulfillment_task_request FOREIGN KEY (service_request_id) REFERENCES service_request(id),
    CONSTRAINT fk_fulfillment_task_assignee FOREIGN KEY (assignee_id) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_fulfillment_task_request ON fulfillment_task(service_request_id);
