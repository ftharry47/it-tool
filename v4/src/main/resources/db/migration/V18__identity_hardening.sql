ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS manager_id UUID NULL;

ALTER TABLE app_user
    ADD CONSTRAINT IF NOT EXISTS fk_app_user_manager
        FOREIGN KEY (manager_id) REFERENCES app_user(id);

CREATE INDEX IF NOT EXISTS idx_app_user_manager_id ON app_user(manager_id);
