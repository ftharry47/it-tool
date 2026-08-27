CREATE TABLE saved_report (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    entity VARCHAR(64) NOT NULL,
    filters TEXT,
    group_by VARCHAR(64),
    date_range TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_saved_report_org_id ON saved_report(org_id);
