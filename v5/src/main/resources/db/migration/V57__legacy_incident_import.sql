-- V57: Historical incident import support
-- Legacy fields keep the source system's ticket identity and requester info
-- without affecting live assignment, SLA, or escalation behavior.
ALTER TABLE incident
    ADD COLUMN IF NOT EXISTS legacy_ticket_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS legacy_requester VARCHAR(255),
    ADD COLUMN IF NOT EXISTS is_legacy_import BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_incident_legacy
    ON incident(org_id, legacy_ticket_id) WHERE legacy_ticket_id IS NOT NULL;

-- Shared placeholder requester for imported tickets whose requester cannot be
-- matched to an existing user. Inactive so it can never authenticate.
INSERT INTO app_user (id, org_id, object_id, email, display_name, status, is_active)
VALUES ('00000000-0000-0000-0000-000000000099',
        '00000000-0000-0000-0000-000000000001',
        'legacy-import', 'legacy-import@alignedcardio.local', 'Legacy Import',
        'INACTIVE', false)
ON CONFLICT ON CONSTRAINT uq_app_user_org_email DO NOTHING;
