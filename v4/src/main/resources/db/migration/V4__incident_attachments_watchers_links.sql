CREATE TABLE IF NOT EXISTS incident_attachment (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID NOT NULL,
    incident_id   UUID NOT NULL,
    file_name     VARCHAR(500) NOT NULL,
    content_type  VARCHAR(255),
    size_bytes    BIGINT,
    blob_url      VARCHAR(1000),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ NULL,
    CONSTRAINT fk_incident_attachment_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_incident_attachment_incident FOREIGN KEY (incident_id) REFERENCES incident(id)
);

CREATE INDEX IF NOT EXISTS idx_incident_attachment_incident ON incident_attachment(incident_id);

CREATE TABLE IF NOT EXISTS incident_watcher (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID NOT NULL,
    incident_id   UUID NOT NULL,
    user_id       UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ NULL,
    CONSTRAINT uq_incident_watcher UNIQUE (incident_id, user_id),
    CONSTRAINT fk_incident_watcher_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_incident_watcher_incident FOREIGN KEY (incident_id) REFERENCES incident(id),
    CONSTRAINT fk_incident_watcher_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_incident_watcher_incident ON incident_watcher(incident_id);
CREATE INDEX IF NOT EXISTS idx_incident_watcher_user ON incident_watcher(user_id);

CREATE TABLE IF NOT EXISTS incident_link (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id           UUID NOT NULL,
    from_incident_id UUID NOT NULL,
    to_incident_id   UUID NOT NULL,
    link_type        VARCHAR(50) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    deleted_at       TIMESTAMPTZ NULL,
    CONSTRAINT uq_incident_link UNIQUE (from_incident_id, to_incident_id, link_type),
    CONSTRAINT fk_incident_link_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_incident_link_from FOREIGN KEY (from_incident_id) REFERENCES incident(id),
    CONSTRAINT fk_incident_link_to FOREIGN KEY (to_incident_id) REFERENCES incident(id)
);

CREATE INDEX IF NOT EXISTS idx_incident_link_from ON incident_link(from_incident_id);
CREATE INDEX IF NOT EXISTS idx_incident_link_to ON incident_link(to_incident_id);

CREATE INDEX IF NOT EXISTS idx_incident_tsv ON incident USING GIN (to_tsvector('english', coalesce(title,'') || ' ' || coalesce(description,'')));
