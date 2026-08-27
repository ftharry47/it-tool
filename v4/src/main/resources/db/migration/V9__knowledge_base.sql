CREATE TABLE IF NOT EXISTS kb_article (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id              UUID NOT NULL,
    number              VARCHAR(20) NOT NULL,
    title               VARCHAR(500) NOT NULL,
    category            VARCHAR(100),
    body                TEXT NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    author_id           UUID NOT NULL,
    view_count          INT NOT NULL DEFAULT 0,
    helpful_count       INT NOT NULL DEFAULT 0,
    not_helpful_count   INT NOT NULL DEFAULT 0,
    version             INT NOT NULL DEFAULT 1,
    published_at        TIMESTAMPTZ,
    archived_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ NULL,
    CONSTRAINT uq_kb_article_number UNIQUE (org_id, number),
    CONSTRAINT fk_kb_article_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_kb_article_author FOREIGN KEY (author_id) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_kb_article_org ON kb_article(org_id);
CREATE INDEX IF NOT EXISTS idx_kb_article_status ON kb_article(status);
CREATE INDEX IF NOT EXISTS idx_kb_article_category ON kb_article(category);

CREATE SEQUENCE IF NOT EXISTS kb_article_number_seq START 1;

CREATE TABLE IF NOT EXISTS kb_article_version (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kb_article_id       UUID NOT NULL,
    version             INT NOT NULL,
    title               VARCHAR(500) NOT NULL,
    category            VARCHAR(100),
    body                TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    CONSTRAINT uq_kb_article_version UNIQUE (kb_article_id, version),
    CONSTRAINT fk_kb_article_version_article FOREIGN KEY (kb_article_id) REFERENCES kb_article(id),
    CONSTRAINT fk_kb_article_version_created_by FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_kb_article_version_article ON kb_article_version(kb_article_id);

CREATE TABLE IF NOT EXISTS kb_feedback (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kb_article_id       UUID NOT NULL,
    helpful             BOOLEAN NOT NULL,
    comment             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    CONSTRAINT fk_kb_feedback_article FOREIGN KEY (kb_article_id) REFERENCES kb_article(id),
    CONSTRAINT fk_kb_feedback_created_by FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_kb_feedback_article ON kb_feedback(kb_article_id);
