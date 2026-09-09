-- Stage D: fulfillment delivery dates + reminder dedup
ALTER TABLE fulfillment_task
    ADD COLUMN expected_delivery_date date,
    ADD COLUMN delivered_at timestamptz,
    ADD COLUMN last_reminded_on date;

CREATE INDEX idx_fulfillment_task_expected_delivery
    ON fulfillment_task (expected_delivery_date)
    WHERE delivered_at IS NULL;

-- Notification rules: delivery date set (requester + IT Fulfillment team)
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Delivery date set - notify requester',
    'Notify the requester when an expected delivery date is set on a fulfillment task',
    'DELIVERY_DATE_SET',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"Delivery date set for {{number}}","body":"Task \"{{taskDescription}}\" on request {{number}} is expected to be delivered by {{expectedDeliveryDate}}.","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);

INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Delivery date set - notify IT Fulfillment team',
    'Confirm to the IT Fulfillment team when a delivery date is set',
    'DELIVERY_DATE_SET',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","teamId":"00000000-0000-0000-0000-000000000010","subject":"Delivery date set: {{number}}","body":"Task \"{{taskDescription}}\" on request {{number}} is expected to be delivered by {{expectedDeliveryDate}}.","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);

-- Notification rules: delivery reminders (requester + IT Fulfillment team)
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Delivery reminder - notify requester',
    'Remind the requester about an upcoming or overdue delivery (3d/1d/due/overdue)',
    'FULFILLMENT_REMINDER',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"Delivery reminder: {{number}}","body":"Reminder ({{phase}}): task \"{{taskDescription}}\" on request {{number}} is expected by {{expectedDeliveryDate}}.","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);

INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Delivery reminder - notify IT Fulfillment team',
    'Remind the IT Fulfillment team about an upcoming or overdue delivery (3d/1d/due/overdue)',
    'FULFILLMENT_REMINDER',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","teamId":"00000000-0000-0000-0000-000000000010","subject":"Delivery reminder: {{number}}","body":"Reminder ({{phase}}): task \"{{taskDescription}}\" on request {{number}} is expected by {{expectedDeliveryDate}}.","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);
