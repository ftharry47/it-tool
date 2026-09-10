-- Add assignee/implementer to change requests.
ALTER TABLE change_request
    ADD COLUMN assignee_id UUID REFERENCES app_user(id);
