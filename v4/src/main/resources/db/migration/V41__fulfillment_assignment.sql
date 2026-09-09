-- Part E: fulfiller assignment on fulfillment tasks.
-- Assignability = membership in the "IT Fulfillment" team
-- (00000000-0000-0000-0000-000000000010, seeded in V31) — no new role.
-- assignee_id already exists; add audit fields for who/when assigned.

ALTER TABLE fulfillment_task
    ADD COLUMN IF NOT EXISTS assigned_at timestamptz,
    ADD COLUMN IF NOT EXISTS assigned_by_id uuid REFERENCES app_user(id);

-- Notification rules for the new lifecycle events.
-- DELIVERY_DATE_SET rules already exist (V32); add the remaining three.

-- Task assigned -> notify the fulfiller who was picked
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Task assigned - notify fulfiller',
    'Notify the assigned fulfiller when a fulfillment task is assigned to them',
    'TASK_ASSIGNED',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{assigneeId}}","subject":"Task assigned: {{number}}","body":"You have been assigned task \"{{taskDescription}}\" on request {{number}} ({{catalogItemName}}).","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);

-- Task delivered -> notify the requester
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Task delivered - notify requester',
    'Notify the requester when a fulfillment task is marked delivered',
    'DELIVERED',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"Delivered: {{number}}","body":"Task \"{{taskDescription}}\" on your request {{number}} ({{catalogItemName}}) has been delivered.","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);

-- Task completed -> notify the requester
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Task completed - notify requester',
    'Notify the requester when a fulfillment task is installed/completed',
    'TASK_COMPLETED',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"Completed: {{number}}","body":"Task \"{{taskDescription}}\" on your request {{number}} ({{catalogItemName}}) has been completed.","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);
