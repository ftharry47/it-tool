-- Mandatory closing notes on fulfillment task completion.
ALTER TABLE fulfillment_task
    ADD COLUMN IF NOT EXISTS closing_notes TEXT;

-- Relabel "delivered" wording to "installed" in existing notification rules
-- (backend enum stays DELIVERED; display wording only).
UPDATE automation_rule
SET actions = '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"Installed: {{number}}","body":"Task \"{{taskDescription}}\" on your service request {{number}} ({{catalogItemName}}) has been installed.","channel":"BOTH"}]'
WHERE trigger_type = 'DELIVERED'
  AND trigger_entity = 'SERVICE_REQUEST'
  AND name = 'Task delivered - notify requester';

-- Delivery date set -> notify the ASSIGNED FULFILLER with location-aware
-- install-reminder wording (requester + team rules already exist in V32).
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Delivery date set - notify assigned fulfiller',
    'Notify the assigned fulfiller with the delivery location so they can plan the install visit',
    'DELIVERY_DATE_SET',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{assigneeId}}","subject":"Install visit: {{number}} on {{expectedDeliveryDate}}","body":"Task \"{{taskDescription}}\" on request {{number}} ({{catalogItemName}}) will be delivered to {{locationName}} on {{expectedDeliveryDate}} - please plan to visit and install.","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);
