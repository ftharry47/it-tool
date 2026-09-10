-- Notify the newly assigned approval manager when a location's
-- approval_manager_user_id is set or changed.
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Location approval manager assigned - notify new manager',
    'Tell the new approval manager that requests for this location will route to them',
    'APPROVAL_MANAGER_ASSIGNED',
    'LOCATION',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{newManagerId}}","subject":"You are now the approval manager for {{locationName}}","body":"You have been assigned as the approval manager for {{locationName}}. Service requests submitted for this location will now route to you for approval.","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);

-- Notify the previous manager when the approval manager is removed,
-- so they know requests for this location no longer route to them.
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Location approval manager removed - notify previous manager',
    'Tell the previous approval manager they are no longer responsible for this location',
    'APPROVAL_MANAGER_REMOVED',
    'LOCATION',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{oldManagerId}}","subject":"You are no longer the approval manager for {{locationName}}","body":"You have been removed as the approval manager for {{locationName}}. Service requests for this location will no longer route to you for approval.","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);
