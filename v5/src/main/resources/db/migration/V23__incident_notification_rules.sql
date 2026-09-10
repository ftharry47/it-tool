-- Seed default incident notification rules that notify the incident reporter
INSERT INTO automation_rule (
    id,
    org_id,
    name,
    description,
    trigger_type,
    trigger_entity,
    trigger_config,
    conditions,
    actions,
    active,
    created_by,
    updated_by
)
VALUES
    (
        gen_random_uuid(),
        '00000000-0000-0000-0000-000000000001'::uuid,
        'Incident status changed notification',
        'Notify the incident reporter when an incident status changes',
        'STATUS_CHANGED',
        'INCIDENT',
        '{}',
        '[]',
        '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"Incident #{{number}} status changed to {{newStatus}}","body":"The status of incident #{{number}} has changed from {{oldStatus}} to {{newStatus}}.","channel":"BOTH"}]',
        true,
        '00000000-0000-0000-0000-000000000000'::uuid,
        '00000000-0000-0000-0000-000000000000'::uuid
    ),
    (
        gen_random_uuid(),
        '00000000-0000-0000-0000-000000000001'::uuid,
        'Incident assigned notification',
        'Notify the incident reporter when an incident is assigned',
        'ASSIGNED',
        'INCIDENT',
        '{}',
        '[]',
        '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"Incident #{{number}} assigned to {{assigneeName}}","body":"Incident #{{number}} has been assigned to {{assigneeName}}.","channel":"BOTH"}]',
        true,
        '00000000-0000-0000-0000-000000000000'::uuid,
        '00000000-0000-0000-0000-000000000000'::uuid
    ),
    (
        gen_random_uuid(),
        '00000000-0000-0000-0000-000000000001'::uuid,
        'Incident commented notification',
        'Notify the incident reporter when a public comment is added',
        'COMMENTED',
        'INCIDENT',
        '{}',
        '[]',
        '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"New comment on Incident #{{number}}","body":"A new public comment has been added to incident #{{number}} by {{authorName}}.","channel":"BOTH"}]',
        true,
        '00000000-0000-0000-0000-000000000000'::uuid,
        '00000000-0000-0000-0000-000000000000'::uuid
    );
