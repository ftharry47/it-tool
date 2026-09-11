-- Email notification redesign.
-- Updates existing rules to the new actions JSON and inserts the new
-- SUBMITTED, CANCELLED and ISSUE rules.
-- Note: SLA_AT_RISK is fired directly by SlaBreachMonitorJob.

-- Incident status changed (requester)
UPDATE automation_rule SET actions = '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"{{number}} \u2014 {{title}} \u2014 Status: {{newStatus}}","body":"Hi {{requesterFirstName}},\n\nThe status of your incident has changed:\n\nIncident {{number}} \u2014 {{title}}\nStatus: {{oldStatus}} \u2192 {{newStatus}}\nChanged by: {{actorName}}\n\nView full ticket:\n{{appBaseUrl}}/dashboard/incidents/{{id}}","channel":"BOTH"}]' WHERE trigger_entity = 'INCIDENT' AND trigger_type = 'STATUS_CHANGED' AND name = 'Incident status changed notification' AND active = true;


-- Incident priority changed (requester)
UPDATE automation_rule SET actions = '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"{{number}} \u2014 {{title}} \u2014 Priority: {{newPriority}}","body":"Hi {{requesterFirstName}},\n\nThe priority of incident {{number}} \u2014 {{title}} has changed:\n\nIncident {{number}} \u2014 {{title}}\nPriority: {{oldPriority}} \u2192 {{newPriority}}\nReason: {{reason}}\nChanged by: {{actorName}}\n\nView full ticket:\n{{appBaseUrl}}/dashboard/incidents/{{id}}","channel":"BOTH"}]' WHERE trigger_entity = 'INCIDENT' AND trigger_type = 'PRIORITY_CHANGED' AND name = 'Incident priority changed - notify requester' AND active = true;


-- Incident created (requester)
UPDATE automation_rule SET actions = '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"{{number}} \u2014 {{title}} \u2014 Incident received","body":"Hi {{requesterFirstName}},\n\nYour incident has been received and is being processed:\n\nIncident {{number}} \u2014 {{title}}\nPriority: {{priority}}\n\nView full ticket:\n{{appBaseUrl}}/dashboard/incidents/{{id}}","channel":"BOTH"}]' WHERE trigger_entity = 'INCIDENT' AND trigger_type = 'CREATED' AND name = 'Incident created notification' AND active = true;


-- Service request pending approval (approver)
UPDATE automation_rule SET actions = '[{"type":"SEND_NOTIFICATION","userId":"{{approverId}}","subject":"{{number}} \u2014 {{catalogItemName}} \u2014 Approval needed","body":"Hi {{approverFirstName}},\n\nA new service request is waiting for your approval:\n\nRequest {{number}} \u2014 {{catalogItemName}}\nRequested by: {{requesterName}}   Location: {{locationName}}\nReason: {{reason}}\n\nPlease review and approve or reject.\n\nView full request:\n{{appBaseUrl}}/dashboard/service-requests/{{id}}","channel":"BOTH"}]' WHERE trigger_entity = 'SERVICE_REQUEST' AND trigger_type = 'PENDING_APPROVAL' AND name = 'Service request pending approval - notify approver' AND active = true;


-- Service request approved (requester)
UPDATE automation_rule SET actions = '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"{{number}} \u2014 {{catalogItemName}} \u2014 Approved","body":"Hi {{requesterFirstName}},\n\nGood news \u2014 your service request has been approved:\n\nRequest {{number}} \u2014 {{catalogItemName}}\nApproved by: {{approverName}}\nNote: {{approvalComment}}\n\nYour request is now moving to fulfillment. We''ll notify you at each step.\n\nView full request:\n{{appBaseUrl}}/dashboard/service-requests/{{id}}","channel":"BOTH"}]' WHERE trigger_entity = 'SERVICE_REQUEST' AND trigger_type = 'APPROVED' AND name = 'Service request approved - notify requester' AND active = true;


-- Service request rejected (requester)
UPDATE automation_rule SET actions = '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"{{number}} \u2014 {{catalogItemName}} \u2014 Rejected","body":"Hi {{requesterFirstName}},\n\nYour service request was not approved:\n\nRequest {{number}} \u2014 {{catalogItemName}}\nRejected by: {{approverName}}\nReason: {{rejectionReason}}\n\nIf you have questions about this decision, please contact your approval manager directly.\n\nView full request:\n{{appBaseUrl}}/dashboard/service-requests/{{id}}","channel":"BOTH"}]' WHERE trigger_entity = 'SERVICE_REQUEST' AND trigger_type = 'REJECTED' AND name = 'Service request rejected - notify requester' AND active = true;


-- Service request ready for fulfillment (IT team)
UPDATE automation_rule SET actions = '[{"type":"SEND_NOTIFICATION","teamId":"00000000-0000-0000-0000-000000000010","subject":"{{number}} \u2014 {{catalogItemName}} \u2014 Needs fulfiller assignment","body":"Hello,\n\nThe following request has been approved and is ready for fulfillment:\n\nRequest {{number}} \u2014 {{catalogItemName}}\nApproved: {{approvedDate}}\n\nPlease assign a fulfiller from the IT Fulfillment team.\n\nView full request:\n{{appBaseUrl}}/dashboard/service-requests/{{id}}","channel":"BOTH"}]' WHERE trigger_entity = 'SERVICE_REQUEST' AND trigger_type = 'IN_FULFILLMENT' AND name = 'Service request ready for fulfillment - notify IT team' AND active = true;


-- Delivery date set (requester)
UPDATE automation_rule SET actions = '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"{{number}} \u2014 {{catalogItemName}} \u2014 Delivery date set","body":"Hi {{requesterFirstName}},\n\nA delivery date has been set for your request:\n\nRequest {{number}} \u2014 {{catalogItemName}}\nTask: {{taskDescription}}\nExpected delivery: {{expectedDeliveryDate}}\n\nWe''ll remind you again as the date approaches.\n\nView full request:\n{{appBaseUrl}}/dashboard/service-requests/{{id}}","channel":"BOTH"}]' WHERE trigger_entity = 'SERVICE_REQUEST' AND trigger_type = 'DELIVERY_DATE_SET' AND name = 'Delivery date set - notify requester' AND active = true;


-- Delivery date set (assigned fulfiller)
UPDATE automation_rule SET actions = '[{"type":"SEND_NOTIFICATION","userId":"{{assigneeId}}","subject":"{{number}} \u2014 {{catalogItemName}} \u2014 Install visit on {{expectedDeliveryDate}}","body":"Hi {{assigneeFirstName}},\n\nTask {{taskDescription}} on request {{number}} ({{catalogItemName}}) will be delivered to {{locationName}} on {{expectedDeliveryDate}} \u2014 please plan to visit and install.\n\nView full request:\n{{appBaseUrl}}/dashboard/service-requests/{{id}}","channel":"BOTH"}]' WHERE trigger_entity = 'SERVICE_REQUEST' AND trigger_type = 'DELIVERY_DATE_SET' AND name = 'Delivery date set - notify assigned fulfiller' AND active = true;


-- Delivery date set (IT team)
UPDATE automation_rule SET actions = '[{"type":"SEND_NOTIFICATION","teamId":"00000000-0000-0000-0000-000000000010","subject":"{{number}} \u2014 {{catalogItemName}} \u2014 Delivery date set","body":"Hello,\n\nA delivery date has been set for request {{number}}:\n\nRequest {{number}} \u2014 {{catalogItemName}}\nTask: {{taskDescription}}\nExpected delivery: {{expectedDeliveryDate}}\n\nView full request:\n{{appBaseUrl}}/dashboard/service-requests/{{id}}","channel":"BOTH"}]' WHERE trigger_entity = 'SERVICE_REQUEST' AND trigger_type = 'DELIVERY_DATE_SET' AND name = 'Delivery date set - notify IT Fulfillment team' AND active = true;


-- Fulfillment delivery reminder (requester)
UPDATE automation_rule SET actions = '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"{{number}} \u2014 {{catalogItemName}} \u2014 Delivery {{phase}}","body":"Hi {{requesterFirstName}},\n\nThis is a reminder about your upcoming delivery:\n\nRequest {{number}} \u2014 {{catalogItemName}}\nTask: {{taskDescription}}\nExpected delivery: {{expectedDeliveryDate}} ({{phase}})\n\nView full request:\n{{appBaseUrl}}/dashboard/service-requests/{{id}}","channel":"BOTH"}]' WHERE trigger_entity = 'SERVICE_REQUEST' AND trigger_type = 'FULFILLMENT_REMINDER' AND name = 'Fulfillment delivery reminder - notify requester' AND active = true;


-- Fulfillment delivery reminder (IT team)
UPDATE automation_rule SET actions = '[{"type":"SEND_NOTIFICATION","teamId":"00000000-0000-0000-0000-000000000010","subject":"{{number}} \u2014 {{catalogItemName}} \u2014 Delivery {{phase}}","body":"Hello,\n\nThis is a reminder about an upcoming delivery:\n\nRequest {{number}} \u2014 {{catalogItemName}}\nTask: {{taskDescription}}\nExpected delivery: {{expectedDeliveryDate}} ({{phase}})\n\nView full request:\n{{appBaseUrl}}/dashboard/service-requests/{{id}}","channel":"BOTH"}]' WHERE trigger_entity = 'SERVICE_REQUEST' AND trigger_type = 'FULFILLMENT_REMINDER' AND name = 'Fulfillment delivery reminder - notify IT team' AND active = true;


-- Delivered (requester)
UPDATE automation_rule SET actions = '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"{{number}} \u2014 {{catalogItemName}} \u2014 Fulfilled","body":"Hi {{requesterFirstName}},\n\nYour service request has been completed:\n\nRequest {{number}} \u2014 {{catalogItemName}}\nStatus: Fulfilled\nCompleted by: {{fulfillerName}}\n\nIf anything isn''t working as expected, please reply or raise a new incident.\n\nView full request:\n{{appBaseUrl}}/dashboard/service-requests/{{id}}","channel":"BOTH"}]' WHERE trigger_entity = 'SERVICE_REQUEST' AND trigger_type = 'DELIVERED' AND name = 'Task delivered - notify requester' AND active = true;


-- Fulfilled (requester)
UPDATE automation_rule SET actions = '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"{{number}} \u2014 {{catalogItemName}} \u2014 Fulfilled","body":"Hi {{requesterFirstName}},\n\nYour service request has been completed:\n\nRequest {{number}} \u2014 {{catalogItemName}}\nStatus: Fulfilled\nCompleted by: {{fulfillerName}}\n\nIf anything isn''t working as expected, please reply or raise a new incident.\n\nView full request:\n{{appBaseUrl}}/dashboard/service-requests/{{id}}","channel":"BOTH"}]' WHERE trigger_entity = 'SERVICE_REQUEST' AND trigger_type = 'FULFILLED' AND name = 'Request fulfilled - notify requester' AND active = true;


-- Location approval manager assigned
UPDATE automation_rule SET actions = '[{"type":"SEND_NOTIFICATION","userId":"{{newManagerId}}","subject":"You are now the approval manager for {{locationName}}","body":"Hi {{managerFirstName}},\n\nYou have been assigned as the approval manager for:\nLocation: {{locationName}}\nService requests submitted for this location will now route to you for approval.\n\nView your approvals queue:\n{{appBaseUrl}}/dashboard/approvals","channel":"BOTH"}]' WHERE trigger_entity = 'LOCATION' AND trigger_type = 'APPROVAL_MANAGER_ASSIGNED' AND name = 'Location approval manager assigned - notify new manager' AND active = true;


-- Location approval manager removed
UPDATE automation_rule SET actions = '[{"type":"SEND_NOTIFICATION","userId":"{{oldManagerId}}","subject":"You are no longer the approval manager for {{locationName}}","body":"Hi {{managerFirstName}},\n\nYou have been removed as the approval manager for location {{locationName}}.\n\nView your approvals queue:\n{{appBaseUrl}}/dashboard/approvals","channel":"BOTH"}]' WHERE trigger_entity = 'LOCATION' AND trigger_type = 'APPROVAL_MANAGER_REMOVED' AND name = 'Location approval manager removed - notify old manager' AND active = true;


-- New rules

-- SERVICE_REQUEST / SUBMITTED -> requester
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Service request submitted - confirm to requester',
    'Confirm receipt of a new service request to the requester',
    'SUBMITTED',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"{{number}} \u2014 {{catalogItemName}} \u2014 Request received","body":"Hi {{requesterFirstName}},\n\nYour service request has been received and is now being processed:\n\nRequest {{number}} \u2014 {{catalogItemName}}\nLocation: {{locationName}}\n\nWe''ll notify you at each step.\n\nView full request:\n{{appBaseUrl}}/dashboard/service-requests/{{id}}","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);


-- SERVICE_REQUEST / CANCELLED -> requester
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Service request cancelled - notify requester',
    'Notify the requester when a service request is cancelled',
    'CANCELLED',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"{{number}} \u2014 {{catalogItemName}} \u2014 Cancelled","body":"Hi {{requesterFirstName}},\n\nThe following service request has been cancelled:\n\nRequest {{number}} \u2014 {{catalogItemName}}\nStatus: Cancelled\n\nIf you did not request this or have questions, please contact your IT team.\n\nView full request:\n{{appBaseUrl}}/dashboard/service-requests/{{id}}","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);


-- SERVICE_REQUEST / CANCELLED -> approver
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Service request cancelled - notify approver',
    'Notify the approver when a service request is cancelled',
    'CANCELLED',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{approverId}}","subject":"{{number}} \u2014 {{catalogItemName}} \u2014 Cancelled","body":"Hi {{approverFirstName}},\n\nThe following service request has been cancelled:\n\nRequest {{number}} \u2014 {{catalogItemName}}\nStatus: Cancelled\n\nIf you have questions, please contact the requester.\n\nView full request:\n{{appBaseUrl}}/dashboard/service-requests/{{id}}","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);


-- SERVICE_REQUEST / CANCELLED -> IT Fulfillment team
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Service request cancelled - notify IT Fulfillment team',
    'Notify the IT Fulfillment team when a service request is cancelled',
    'CANCELLED',
    'SERVICE_REQUEST',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","teamId":"00000000-0000-0000-0000-000000000010","subject":"{{number}} \u2014 {{catalogItemName}} \u2014 Cancelled","body":"Hello,\n\nThe following service request has been cancelled:\n\nRequest {{number}} \u2014 {{catalogItemName}}\nStatus: Cancelled\n\nView full request:\n{{appBaseUrl}}/dashboard/service-requests/{{id}}","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);


-- ISSUE / CREATED -> requester
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Issue created - notify requester',
    'Notify the issue reporter when a new issue is created',
    'CREATED',
    'ISSUE',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{requesterId}}","subject":"{{projectKey}}: {{key}} \u2014 Issue created","body":"Hi {{recipientFirstName}},\n\nA new issue has been created in {{projectKey}}:\n\nIssue {{key}} \u2014 {{summary}}\nPriority: {{priority}}\n\nView issue:\n{{appBaseUrl}}/dashboard/projects/{{projectId}}/issues/{{id}}","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);


-- ISSUE / CREATED -> assignee
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Issue created - notify assignee',
    'Notify the assignee when a new issue is created',
    'CREATED',
    'ISSUE',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{assigneeId}}","subject":"{{projectKey}}: {{key}} \u2014 Issue created","body":"Hi {{recipientFirstName}},\n\nA new issue has been assigned to you in {{projectKey}}:\n\nIssue {{key}} \u2014 {{summary}}\nPriority: {{priority}}\n\nView issue:\n{{appBaseUrl}}/dashboard/projects/{{projectId}}/issues/{{id}}","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);


-- ISSUE / CREATED -> project lead
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Issue created - notify project lead',
    'Notify the project lead when a new issue is created',
    'CREATED',
    'ISSUE',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{leadId}}","subject":"{{projectKey}}: {{key}} \u2014 Issue created","body":"Hi {{recipientFirstName}},\n\nA new issue has been created in {{projectKey}}:\n\nIssue {{key}} \u2014 {{summary}}\nPriority: {{priority}}\n\nView issue:\n{{appBaseUrl}}/dashboard/projects/{{projectId}}/issues/{{id}}","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);


-- ISSUE / STATUS_CHANGED -> assignee
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Issue status changed - notify assignee',
    'Notify the assignee when an issue status changes',
    'STATUS_CHANGED',
    'ISSUE',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{assigneeId}}","subject":"{{projectKey}}: {{key}} \u2014 Status: {{newStatus}}","body":"Hi {{recipientFirstName}},\n\nThe status of issue {{key}} has changed:\n\nIssue {{key}} \u2014 {{summary}}\nStatus: {{oldStatus}} \u2192 {{newStatus}}\n\nView issue:\n{{appBaseUrl}}/dashboard/projects/{{projectId}}/issues/{{id}}","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);


-- ISSUE / STATUS_CHANGED -> project lead
INSERT INTO automation_rule (
    id, org_id, name, description, trigger_type, trigger_entity,
    trigger_config, conditions, actions, active, created_by, updated_by
)
VALUES (
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Issue status changed - notify project lead',
    'Notify the project lead when an issue status changes',
    'STATUS_CHANGED',
    'ISSUE',
    '{}',
    '[]',
    '[{"type":"SEND_NOTIFICATION","userId":"{{leadId}}","subject":"{{projectKey}}: {{key}} \u2014 Status: {{newStatus}}","body":"Hi {{recipientFirstName}},\n\nThe status of issue {{key}} has changed:\n\nIssue {{key}} \u2014 {{summary}}\nStatus: {{oldStatus}} \u2192 {{newStatus}}\n\nView issue:\n{{appBaseUrl}}/dashboard/projects/{{projectId}}/issues/{{id}}","channel":"BOTH"}]',
    true,
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid
);
