-- Add location and phone fields to incident table
ALTER TABLE incident
ADD COLUMN location VARCHAR(255),
ADD COLUMN phone VARCHAR(20);

-- Create index for location searches
CREATE INDEX idx_incident_location ON incident(org_id, location);
