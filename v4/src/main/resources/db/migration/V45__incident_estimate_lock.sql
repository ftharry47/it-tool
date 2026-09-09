-- One-time estimate lock: the assigned agent may set the estimate once per
-- assignment. estimate_set_at non-null = locked. Cleared on reassignment,
-- tier escalation, or reopen so the new owner gets one fresh opportunity.
ALTER TABLE incident
    ADD COLUMN IF NOT EXISTS estimate_set_at timestamptz,
    ADD COLUMN IF NOT EXISTS estimate_set_by_id uuid REFERENCES app_user(id);
