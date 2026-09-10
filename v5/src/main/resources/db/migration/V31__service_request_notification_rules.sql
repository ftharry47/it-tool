-- Seed default service-request notification rules (automation engine, not hardcoded)
-- trigger_entity = 'SERVICE_REQUEST', trigger_type matches ServiceRequestEvent.triggerType()

INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Service request pending approval - notify approver',
    'Notify the resolved approver when a service request is routed to them',
    'PENDING_APPROVAL',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{approverId}}","subject":"Approval needed: {{number}}","body":"Service request {{number}} ({{catalogItemName}}) is waiting for your approval.","channel":"BOTH"}]',
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
    'Service request approved - notify requester',
    'Notify the requester when their service request is approved',
    'APPROVED',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"Request {{number}} approved","body":"Your service request {{number}} ({{catalogItemName}}) was approved by {{approverName}} and is moving to fulfillment.","channel":"BOTH"}]',
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
    'Service request rejected - notify requester',
    'Notify the requester when their service request is rejected, including the reason',
    'REJECTED',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"Request {{number}} rejected","body":"Your service request {{number}} ({{catalogItemName}}) was rejected by {{approverName}}. Reason: {{reason}}","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);

-- Default fulfillment team for service-request notifications.
-- Members are managed via the existing team admin APIs; add AGENT+ users here.
INSERT INTO team (id, org_id, name, description, created_by, updated_by)
VALUES (
    '00000000-0000-0000-0000-000000000010'::uuid,
    '00000000-0000-0000-0000-000000000001'::uuid,
    'IT Fulfillment',
    'Default team notified when a service request is ready to fulfill',
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Service request ready for fulfillment - notify IT team',
    'Notify IT Fulfillment team members when a service request is ready to fulfill',
    'IN_FULFILLMENT',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","teamId":"00000000-0000-0000-0000-000000000010","subject":"Request {{number}} ready to fulfill","body":"Service request {{number}} ({{catalogItemName}}) is approved and ready for fulfillment.","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);
