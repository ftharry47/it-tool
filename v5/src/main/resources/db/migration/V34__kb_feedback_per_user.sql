-- One feedback vote per user per article. created_by already stores the voter.
-- Remove duplicate votes (keep the most recent) before adding the constraint.
DELETE FROM kb_feedback a
USING kb_feedback b
WHERE a.kb_article_id = b.kb_article_id
  AND a.created_by = b.created_by
  AND a.created_at < b.created_at;

CREATE UNIQUE INDEX ux_kb_feedback_article_user
    ON kb_feedback (kb_article_id, created_by);
