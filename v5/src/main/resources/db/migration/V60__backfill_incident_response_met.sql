-- Backfill response_met_at for incidents worked without a public comment.
--
-- Before the responseMetAt fix, only PUBLIC comments set response_met_at —
-- status changes and assignment never reached it. Every incident that left
-- NEW via agent action alone (including all resolved/closed ones) has a
-- permanently "calculating" response clock in the UI.
--
-- Backfill value = earliest evidence of agent engagement:
--   a) first public comment authored by someone other than the requester, or
--   b) first audit-log action implying agent touch (assign/status/escalate), or
--   c) resolution_met_at (engagement provably happened no later than
--      resolution — conservative upper bound), or
--   d) incident.updated_at (last resort for legacy rows with no audit).
--
-- Idempotent: only fills rows where response_met_at IS NULL, and only for
-- incidents that actually left NEW (genuinely untouched NEW tickets keep a
-- legitimately-running clock).

UPDATE sla_instance si
SET response_met_at = COALESCE(
        LEAST(
            (SELECT MIN(c.created_at) FROM incident_comment c
             WHERE c.incident_id = si.incident_id
               AND c.is_public = true
               AND c.author_id <> i.requester_id),
            (SELECT MIN(al.created_at) FROM audit_log al
             WHERE al.org_id = si.org_id
               AND al.entity_type = 'INCIDENT'
               AND al.entity_id = si.incident_id
               AND al.action IN ('ASSIGN', 'REASSIGN', 'STATUS', 'UPDATE', 'REOPEN',
                                 'ESCALATE_PRIORITY', 'ESCALATE_TIER', 'AUTO_ESCALATE_TIER'))
        ),
        (SELECT MIN(c.created_at) FROM incident_comment c
         WHERE c.incident_id = si.incident_id
           AND c.is_public = true
           AND c.author_id <> i.requester_id),
        (SELECT MIN(al.created_at) FROM audit_log al
         WHERE al.org_id = si.org_id
           AND al.entity_type = 'INCIDENT'
           AND al.entity_id = si.incident_id
           AND al.action IN ('ASSIGN', 'REASSIGN', 'STATUS', 'UPDATE', 'REOPEN',
                             'ESCALATE_PRIORITY', 'ESCALATE_TIER', 'AUTO_ESCALATE_TIER')),
        si.resolution_met_at,
        i.updated_at)
FROM incident i
WHERE si.incident_id = i.id
  AND i.status <> 'NEW'
  AND si.response_met_at IS NULL;
