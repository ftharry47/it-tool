ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS manager_id UUID NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_app_user_manager'
          AND conrelid = 'app_user'::regclass
    ) THEN
        ALTER TABLE app_user
            ADD CONSTRAINT fk_app_user_manager
            FOREIGN KEY (manager_id) REFERENCES app_user(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_app_user_manager_id ON app_user(manager_id);
