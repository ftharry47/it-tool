ALTER TABLE incident
    ADD COLUMN IF NOT EXISTS assignment_team_id UUID,
    ADD CONSTRAINT fk_incident_assignment_team
        FOREIGN KEY (assignment_team_id) REFERENCES team(id);

CREATE INDEX IF NOT EXISTS idx_incident_assignment_team ON incident(assignment_team_id);

ALTER TABLE issue
    ADD COLUMN IF NOT EXISTS assignment_team_id UUID,
    ADD CONSTRAINT fk_issue_assignment_team
        FOREIGN KEY (assignment_team_id) REFERENCES team(id);

CREATE INDEX IF NOT EXISTS idx_issue_assignment_team ON issue(assignment_team_id);
