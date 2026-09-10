-- Seed default incident creation notification rule
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
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Incident created notification',
    'Notify the incident reporter when an incident is created',
    'CREATED',
    'INCIDENT',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"Incident #{{number}} created","body":"Your incident #{{number}} has been created and is currently {{status}}.","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);
