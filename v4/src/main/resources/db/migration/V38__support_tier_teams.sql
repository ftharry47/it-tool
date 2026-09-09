-- Seed the global L1/L2/L3 support-tier teams (one set for the whole org).
-- These start EMPTY: members are managed via the Support Tiers admin page
-- (/admin/support-tiers) or the team admin APIs. Until populated, SLA
-- escalation reassignments/notifications targeting these tiers go nowhere.
-- When sla_escalation_tier rows are configured, reassign_to_team_id should
-- point at these IDs (L1=...0020, L2=...0021, L3=...0022).

INSERT INTO team (id, org_id, name, description, created_by, updated_by)
VALUES (
    '00000000-0000-0000-0000-000000000020'::uuid,
    '00000000-0000-0000-0000-000000000001'::uuid,
    'L1 Support',
    'First-line support tier - initial triage and resolution',
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO team (id, org_id, name, description, created_by, updated_by)
VALUES (
    '00000000-0000-0000-0000-000000000021'::uuid,
    '00000000-0000-0000-0000-000000000001'::uuid,
    'L2 Support',
    'Second support tier - escalated incidents needing deeper investigation',
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO team (id, org_id, name, description, created_by, updated_by)
VALUES (
    '00000000-0000-0000-0000-000000000022'::uuid,
    '00000000-0000-0000-0000-000000000001'::uuid,
    'L3 Support',
    'Third support tier - highest expertise for complex or persistent issues',
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
)
ON CONFLICT (id) DO NOTHING;
