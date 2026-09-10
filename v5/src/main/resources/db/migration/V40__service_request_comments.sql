-- Comment thread on service requests, mirroring incident_comment:
-- is_public split (END_USER never sees internal comments), author_type
-- distinguishes user vs automation comments.

CREATE TABLE IF NOT EXISTS service_request_comment (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id             UUID NOT NULL,
    service_request_id UUID NOT NULL,
    author_id          UUID NOT NULL,
    body               TEXT NOT NULL,
    is_public          BOOLEAN NOT NULL DEFAULT true,
    author_type        VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    deleted_at         TIMESTAMPTZ NULL,
    CONSTRAINT fk_sr_comment_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_sr_comment_request FOREIGN KEY (service_request_id) REFERENCES service_request(id),
    CONSTRAINT fk_sr_comment_author FOREIGN KEY (author_id) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_sr_comment_request ON service_request_comment(service_request_id);

CREATE OR REPLACE TRIGGER trg_sr_comment_updated_at
BEFORE UPDATE ON service_request_comment
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
