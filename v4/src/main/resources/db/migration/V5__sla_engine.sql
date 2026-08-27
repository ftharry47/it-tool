DROP TABLE IF EXISTS incident_sla CASCADE;
DROP TABLE IF EXISTS sla_definition CASCADE;
DROP TABLE IF EXISTS sla_policy CASCADE;

CREATE TABLE IF NOT EXISTS business_calendar (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id         UUID NOT NULL,
    name           VARCHAR(255) NOT NULL,
    timezone       VARCHAR(64) NOT NULL DEFAULT 'UTC',
    working_hours  JSONB NOT NULL DEFAULT '{"monday": {"start": "09:00", "end": "17:00"}, "tuesday": {"start": "09:00", "end": "17:00"}, "wednesday": {"start": "09:00", "end": "17:00"}, "thursday": {"start": "09:00", "end": "17:00"}, "friday": {"start": "09:00", "end": "17:00"}}'::jsonb,
    holidays       JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID,
    deleted_at     TIMESTAMPTZ NULL,
    CONSTRAINT fk_business_calendar_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_business_calendar_org ON business_calendar(org_id);

CREATE TABLE IF NOT EXISTS sla_policy (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                     UUID NOT NULL,
    name                       VARCHAR(255) NOT NULL,
    applies_to                 VARCHAR(32) NOT NULL DEFAULT 'INCIDENT',
    priority_filter            VARCHAR(32) NULL,
    response_target_minutes    INT NOT NULL DEFAULT 60,
    resolution_target_minutes  INT NOT NULL DEFAULT 480,
    business_hours_calendar_id UUID,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                 UUID,
    updated_by                 UUID,
    deleted_at                 TIMESTAMPTZ NULL,
    CONSTRAINT fk_sla_policy_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_sla_policy_calendar FOREIGN KEY (business_hours_calendar_id) REFERENCES business_calendar(id)
);

CREATE INDEX IF NOT EXISTS idx_sla_policy_org ON sla_policy(org_id);
CREATE INDEX IF NOT EXISTS idx_sla_policy_calendar ON sla_policy(business_hours_calendar_id);

CREATE TABLE IF NOT EXISTS sla_instance (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                   UUID NOT NULL,
    sla_policy_id            UUID NOT NULL,
    incident_id              UUID NULL,
    service_request_id       UUID NULL,
    response_due_at          TIMESTAMPTZ,
    resolution_due_at        TIMESTAMPTZ,
    response_met_at          TIMESTAMPTZ NULL,
    resolution_met_at        TIMESTAMPTZ NULL,
    paused_at                TIMESTAMPTZ NULL,
    total_paused_minutes     INT NOT NULL DEFAULT 0,
    breach_status            VARCHAR(32) NOT NULL DEFAULT 'ON_TRACK',
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ NULL,
    CONSTRAINT uq_sla_instance_incident UNIQUE (incident_id),
    CONSTRAINT uq_sla_instance_request UNIQUE (service_request_id),
    CONSTRAINT fk_sla_instance_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_sla_instance_policy FOREIGN KEY (sla_policy_id) REFERENCES sla_policy(id),
    CONSTRAINT fk_sla_instance_incident FOREIGN KEY (incident_id) REFERENCES incident(id)
);

CREATE INDEX IF NOT EXISTS idx_sla_instance_policy ON sla_instance(sla_policy_id);
CREATE INDEX IF NOT EXISTS idx_sla_instance_incident ON sla_instance(incident_id);
CREATE INDEX IF NOT EXISTS idx_sla_instance_breach ON sla_instance(breach_status);
