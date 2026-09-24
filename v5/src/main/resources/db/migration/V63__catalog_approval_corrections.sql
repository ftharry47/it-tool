-- Approval corrections: licensed spend and hardware purchases require
-- approval (ServiceNow convention). Name-keyed UPDATE — idempotent and
-- does not touch any other column or manually-changed fields.
UPDATE catalog_item
SET approval_required = true, updated_at = now()
WHERE name IN (
    'Clinical Software',
    'Clinical Workstation / Device',
    'Mobile Device / BYOD'
);
