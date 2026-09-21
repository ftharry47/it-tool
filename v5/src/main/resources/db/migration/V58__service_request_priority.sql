-- V58: service requests get an optional SLA-driving priority, matching
-- incident/change parity so REQUEST policies can filter by priority.
ALTER TABLE service_request
    ADD COLUMN IF NOT EXISTS priority_id uuid REFERENCES priority(id);
