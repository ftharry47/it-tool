-- Seed notification rules for two events that previously fired but notified nobody:
--   1. FULFILLMENT_REMINDER (ServiceRequestEvent, FulfillmentReminderJob) -> requester + IT Fulfillment team
--   2. APPROVAL_MANAGER_ASSIGNED / APPROVAL_MANAGER_REMOVED (LocationApprovalManagerChangedEvent) -> new + old manager
-- pushTitle/pushBody are optional; when absent the push text is auto-derived from subject/body.

INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Fulfillment delivery reminder - notify requester',
    'Remind the requester as the expected delivery date approaches (3-day, 1-day, due today, overdue)',
    'FULFILLMENT_REMINDER',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"{{number}} – {{catalogItemName}} – Delivery {{phase}}","body":"Reminder for service request {{number}} ({{catalogItemName}}): task \"{{taskDescription}}\" is due {{expectedDeliveryDate}} ({{phase}}).","pushTitle":"{{number}} – Delivery {{phase}}","pushBody":"{{taskDescription}} due {{expectedDeliveryDate}}","channel":"BOTH"}]',
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
    'Fulfillment delivery reminder - notify IT team',
    'Remind the IT Fulfillment team as the expected delivery date approaches',
    'FULFILLMENT_REMINDER',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","teamId":"00000000-0000-0000-0000-000000000010","subject":"{{number}} – {{catalogItemName}} – Delivery {{phase}}","body":"Delivery reminder for service request {{number}} ({{catalogItemName}}): task \"{{taskDescription}}\" is due {{expectedDeliveryDate}} ({{phase}}).","pushTitle":"{{number}} – Delivery {{phase}}","pushBody":"{{taskDescription}} due {{expectedDeliveryDate}}","channel":"BOTH"}]',
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
    'Location approval manager assigned - notify new manager',
    'Notify the newly assigned approval manager for a location',
    'APPROVAL_MANAGER_ASSIGNED',
    'LOCATION',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{newManagerId}}","subject":"You are now the approval manager for {{locationName}}","body":"You have been assigned as the approval manager for location {{locationName}}. You will receive approval requests for this location.","pushTitle":"Approval manager assigned","pushBody":"You now manage approvals for {{locationName}}","channel":"BOTH"}]',
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
    'Location approval manager removed - notify old manager',
    'Notify the previous approval manager when they are removed from a location',
    'APPROVAL_MANAGER_REMOVED',
    'LOCATION',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{oldManagerId}}","subject":"You are no longer the approval manager for {{locationName}}","body":"You have been removed as the approval manager for location {{locationName}}.","pushTitle":"Approval manager removed","pushBody":"You no longer manage approvals for {{locationName}}","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);
