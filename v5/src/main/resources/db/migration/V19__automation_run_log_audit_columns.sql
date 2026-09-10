ALTER TABLE automation_run_log
    ADD COLUMN IF NOT EXISTS created_by UUID,
    ADD COLUMN IF NOT EXISTS updated_by UUID,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

DROP TRIGGER IF EXISTS trg_automation_run_log_updated_at ON automation_run_log;
CREATE TRIGGER trg_automation_run_log_updated_at
    BEFORE UPDATE ON automation_run_log
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
