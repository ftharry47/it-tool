CREATE TABLE IF NOT EXISTS location (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                    UUID NOT NULL,
    name                      VARCHAR(255) NOT NULL,
    address                   TEXT,
    approval_manager_user_id  UUID,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                UUID,
    updated_by                UUID,
    deleted_at                TIMESTAMPTZ NULL,
    CONSTRAINT fk_location_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_location_approval_manager FOREIGN KEY (approval_manager_user_id) REFERENCES app_user(id),
    CONSTRAINT uq_location_org_name UNIQUE (org_id, name)
);

CREATE INDEX IF NOT EXISTS idx_location_org ON location(org_id);
CREATE INDEX IF NOT EXISTS idx_location_approval_manager ON location(approval_manager_user_id);

CREATE OR REPLACE TRIGGER trg_location_updated_at
BEFORE UPDATE ON location
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

INSERT INTO location (org_id, name, created_by, updated_by)
VALUES
('00000000-0000-0000-0000-000000000001'::uuid, 'Aligned Cardio Partners - Corporate Office - Richmond, VA', '00000000-0000-0000-0000-000000000000'::uuid, '00000000-0000-0000-0000-000000000000'::uuid),
('00000000-0000-0000-0000-000000000001'::uuid, 'Aligned Interventional Center - Richmond, VA', '00000000-0000-0000-0000-000000000000'::uuid, '00000000-0000-0000-0000-000000000000'::uuid),
('00000000-0000-0000-0000-000000000001'::uuid, 'Aligned Surgical Center - Colonial Heights, VA', '00000000-0000-0000-0000-000000000000'::uuid, '00000000-0000-0000-0000-000000000000'::uuid),
('00000000-0000-0000-0000-000000000001'::uuid, 'Colonial Heart - Williamsburg, VA', '00000000-0000-0000-0000-000000000000'::uuid, '00000000-0000-0000-0000-000000000000'::uuid),
('00000000-0000-0000-0000-000000000001'::uuid, 'Heart Rhythm Associates - Greenville, NC', '00000000-0000-0000-0000-000000000000'::uuid, '00000000-0000-0000-0000-000000000000'::uuid),
('00000000-0000-0000-0000-000000000001'::uuid, 'James River Cardiology - Chesterfield, VA', '00000000-0000-0000-0000-000000000000'::uuid, '00000000-0000-0000-0000-000000000000'::uuid),
('00000000-0000-0000-0000-000000000001'::uuid, 'James River Cardiology - Colonial Heights, VA', '00000000-0000-0000-0000-000000000000'::uuid, '00000000-0000-0000-0000-000000000000'::uuid),
('00000000-0000-0000-0000-000000000001'::uuid, 'James River Cardiology - Discovery, VA', '00000000-0000-0000-0000-000000000000'::uuid, '00000000-0000-0000-0000-000000000000'::uuid),
('00000000-0000-0000-0000-000000000001'::uuid, 'James River Cardiology - Emporia, VA', '00000000-0000-0000-0000-000000000000'::uuid, '00000000-0000-0000-0000-000000000000'::uuid),
('00000000-0000-0000-0000-000000000001'::uuid, 'James River Cardiology - Franklin, VA', '00000000-0000-0000-0000-000000000000'::uuid, '00000000-0000-0000-0000-000000000000'::uuid),
('00000000-0000-0000-0000-000000000001'::uuid, 'James River Cardiology - Lawrenceville, VA', '00000000-0000-0000-0000-000000000000'::uuid, '00000000-0000-0000-0000-000000000000'::uuid),
('00000000-0000-0000-0000-000000000001'::uuid, 'NOVA Cardiovascular Care - Stafford, VA', '00000000-0000-0000-0000-000000000000'::uuid, '00000000-0000-0000-0000-000000000000'::uuid),
('00000000-0000-0000-0000-000000000001'::uuid, 'NOVA Cardiovascular Care - Woodbridge, VA', '00000000-0000-0000-0000-000000000000'::uuid, '00000000-0000-0000-0000-000000000000'::uuid),
('00000000-0000-0000-0000-000000000001'::uuid, 'Potomac Cardiovascular Care - Potomac, VA', '00000000-0000-0000-0000-000000000000'::uuid, '00000000-0000-0000-0000-000000000000'::uuid)
ON CONFLICT (org_id, name) DO NOTHING;

ALTER TABLE incident
ADD COLUMN IF NOT EXISTS location_id UUID;

ALTER TABLE service_request
ADD COLUMN IF NOT EXISTS location_id UUID;

INSERT INTO location (org_id, name, created_by, updated_by)
SELECT DISTINCT i.org_id, i.location, '00000000-0000-0000-0000-000000000000'::uuid, '00000000-0000-0000-0000-000000000000'::uuid
FROM incident i
WHERE i.location IS NOT NULL
  AND btrim(i.location) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM location l
      WHERE l.org_id = i.org_id
        AND lower(l.name) = lower(i.location)
        AND l.deleted_at IS NULL
  );

UPDATE incident i
SET location_id = l.id
FROM location l
WHERE i.location_id IS NULL
  AND i.location IS NOT NULL
  AND l.org_id = i.org_id
  AND lower(l.name) = lower(i.location)
  AND l.deleted_at IS NULL;

ALTER TABLE incident
ADD CONSTRAINT fk_incident_location FOREIGN KEY (location_id) REFERENCES location(id);

ALTER TABLE service_request
ADD CONSTRAINT fk_service_request_location FOREIGN KEY (location_id) REFERENCES location(id);

CREATE INDEX IF NOT EXISTS idx_incident_location_id ON incident(org_id, location_id);
CREATE INDEX IF NOT EXISTS idx_service_request_location_id ON service_request(org_id, location_id);
