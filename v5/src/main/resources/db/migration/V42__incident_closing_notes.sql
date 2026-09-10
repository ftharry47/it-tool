-- Mandatory closing notes for CLOSED incidents (staff-only visibility).
ALTER TABLE incident
    ADD COLUMN IF NOT EXISTS closing_notes text;
