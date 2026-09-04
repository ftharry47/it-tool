-- Coerce automation_run_log.entity_id to UUID to match the JPA entity.
-- This is idempotent: on a fresh DB it is already UUID, and on the existing DB it is already UUID.
ALTER TABLE automation_run_log
    ALTER COLUMN entity_id SET DATA TYPE UUID USING entity_id::uuid;
