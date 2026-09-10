-- Notification coverage gaps found in the event/rule audit:
--
-- 1. Events that fired with NO matching rule (silent on every channel):
--      INCIDENT / PRIORITY_CHANGED  (IncidentService publishes IncidentPriorityChangedEvent)
--      SERVICE_REQUEST / FULFILLED  (ServiceRequestService.completeTask now publishes it)
--      SERVICE_REQUEST / TASK_ORDERED (ServiceRequestService.markOrdered now publishes it)
--
-- 2. Duplicate rules seeded twice by earlier migrations (each event notified
--    recipients TWICE). Deactivate the older copies, keeping the newer
--    pushTitle/pushBody-enhanced versions from V37:
--      FULFILLMENT_REMINDER  : V32 pair (older) vs V37 pair (kept)
--      APPROVAL_MANAGER_*    : V33 pair (older) vs V37 pair (kept)

-- Deactivate the older duplicate FULFILLMENT_REMINDER rules (V32 names).
UPDATE automation_rule
SET active = false
WHERE trigger_type = 'FULFILLMENT_REMINDER'
  AND trigger_entity = 'SERVICE_REQUEST'
  AND name IN ('Delivery reminder - notify requester',
               'Delivery reminder - notify IT Fulfillment team');

-- Deactivate the older duplicate location approval-manager rules (V33 names).
UPDATE automation_rule
SET active = false
WHERE trigger_entity = 'LOCATION'
  AND trigger_type IN ('APPROVAL_MANAGER_ASSIGNED', 'APPROVAL_MANAGER_REMOVED')
  AND name IN ('Approval manager assigned - notify new manager',
               'Approval manager removed - notify old manager');

-- INCIDENT / PRIORITY_CHANGED -> notify the requester.
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Incident priority changed - notify requester',
    'Notify the incident reporter when the incident priority changes',
    'PRIORITY_CHANGED',
    'INCIDENT',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"Incident #{{number}} priority changed","body":"The priority of incident #{{number}} has been updated.","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);

-- SERVICE_REQUEST / TASK_ORDERED -> notify the requester that the order was placed.
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Task ordered - notify requester',
    'Notify the requester when a fulfillment task is marked ordered',
    'TASK_ORDERED',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"Order placed: {{number}}","body":"Task \"{{taskDescription}}\" on your request {{number}} ({{catalogItemName}}) has been ordered.","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);

-- SERVICE_REQUEST / FULFILLED -> notify the requester the whole request is done.
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Request fulfilled - notify requester',
    'Notify the requester when all fulfillment tasks are completed and the request is fulfilled',
    'FULFILLED',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"Request fulfilled: {{number}}","body":"Your service request {{number}} ({{catalogItemName}}) has been fulfilled.","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);
