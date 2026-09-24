-- Optional workflow-type filter on SLA policies (mirrors priority_filter).
-- REQUEST policies may restrict to FULL / SOFTWARE / INSTANT fulfillment
-- workflows so e.g. an INSTANT "password reset" can carry a short target
-- while a FULL "new workstation" carries a multi-day one. NULL = any.
ALTER TABLE sla_policy ADD COLUMN workflow_type VARCHAR(20) NULL;
