-- Web Push subscriptions (VAPID) + per-channel push status on notification
-- + master push opt-in flag on notification_preference.

CREATE TABLE IF NOT EXISTS push_subscription (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       UUID NOT NULL,
    user_id      UUID NOT NULL,
    endpoint     TEXT NOT NULL,
    p256dh       TEXT NOT NULL,
    auth         TEXT NOT NULL,
    user_agent   VARCHAR(255),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID,
    deleted_at   TIMESTAMPTZ NULL,
    CONSTRAINT fk_push_sub_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_push_sub_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT uq_push_sub_user_endpoint UNIQUE (user_id, endpoint)
);

CREATE INDEX IF NOT EXISTS idx_push_sub_user ON push_subscription(user_id);
CREATE INDEX IF NOT EXISTS idx_push_sub_org ON push_subscription(org_id);

ALTER TABLE notification
    ADD COLUMN IF NOT EXISTS push_status VARCHAR(16) NULL;

ALTER TABLE notification_preference
    ADD COLUMN IF NOT EXISTS push_enabled BOOLEAN NOT NULL DEFAULT false;

CREATE OR REPLACE TRIGGER trg_push_subscription_updated_at
BEFORE UPDATE ON push_subscription
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
