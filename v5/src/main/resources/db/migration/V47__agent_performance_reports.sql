-- Extend saved_report for auto-generated agent performance reports.
-- report_type distinguishes user-saved ad-hoc queries ('AD_HOC') from
-- system-generated monthly performance reports ('AGENT_PERFORMANCE').
-- owner_user_id scopes a generated report to the agent it belongs to.
-- payload stores the frozen report content (metrics + score) as JSON.
ALTER TABLE saved_report
    ADD COLUMN report_type VARCHAR(32) NOT NULL DEFAULT 'AD_HOC',
    ADD COLUMN owner_user_id UUID NULL,
    ADD COLUMN payload TEXT NULL;

CREATE INDEX idx_saved_report_owner ON saved_report (owner_user_id) WHERE owner_user_id IS NOT NULL;
CREATE INDEX idx_saved_report_type ON saved_report (report_type);
