CREATE TABLE notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL,
    user_id UUID NOT NULL,
    type VARCHAR(64) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    entity_type VARCHAR(64),
    entity_id UUID,
    channel VARCHAR(16) NOT NULL,
    in_app_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    email_status VARCHAR(16),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_notification_user_id ON notification(user_id);
CREATE INDEX idx_notification_org_id ON notification(org_id);
CREATE INDEX idx_notification_email_status ON notification(email_status) WHERE email_status IS NOT NULL;

CREATE TABLE notification_preference (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL,
    user_id UUID NOT NULL UNIQUE,
    in_app_enabled BOOLEAN NOT NULL DEFAULT true,
    email_enabled BOOLEAN NOT NULL DEFAULT true,
    email_address VARCHAR(255),
    digest_mode VARCHAR(16) NOT NULL DEFAULT 'NONE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_preference_user_id ON notification_preference(user_id);
