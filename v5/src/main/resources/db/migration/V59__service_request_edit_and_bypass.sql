-- SUPER_ADMIN admin-edit + approval-bypass override on service requests.
-- approval_bypassed / bypassed_by_id / bypass_reason keep a bypass visibly
-- distinct from a decision the designated approver actually made.
ALTER TABLE service_request
    ADD COLUMN IF NOT EXISTS approval_bypassed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS bypassed_by_id UUID REFERENCES app_user(id),
    ADD COLUMN IF NOT EXISTS bypass_reason TEXT;
