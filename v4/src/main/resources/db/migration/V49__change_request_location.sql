ALTER TABLE change_request
    ADD COLUMN IF NOT EXISTS location_id UUID;

ALTER TABLE change_request
    ADD CONSTRAINT fk_change_location FOREIGN KEY (location_id) REFERENCES location(id);

CREATE INDEX IF NOT EXISTS idx_change_location ON change_request(location_id);
