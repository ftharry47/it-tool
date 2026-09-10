# Schema Audit Report (V1–V19)

## BaseEntity Tables Missing Required Columns or `updated_at` Trigger

| Table | Entity File | Missing BaseEntity Columns | Missing updated_at Trigger |
|---|---|---|---|
| business_calendar | BusinessCalendar.java | - | Yes |
| catalog_item | CatalogItem.java | - | Yes |
| change_approval | ChangeApproval.java | org_id | Yes |
| change_request | ChangeRequest.java | - | Yes |
| fulfillment_task | FulfillmentTask.java | org_id | Yes |
| incident_attachment | IncidentAttachment.java | - | Yes |
| incident_link | IncidentLink.java | - | Yes |
| incident_watcher | IncidentWatcher.java | - | Yes |
| issue | Issue.java | - | Yes |
| issue_comment | IssueComment.java | deleted_at | Yes |
| issue_link | IssueLink.java | deleted_at, updated_at, updated_by | Yes |
| issue_type | IssueType.java | - | Yes |
| kb_article | KbArticle.java | - | Yes |
| kb_article_version | KbArticleVersion.java | deleted_at, org_id, updated_at, updated_by | Yes |
| kb_feedback | KbFeedback.java | deleted_at, org_id, updated_at, updated_by | Yes |
| notification | Notification.java | - | Yes |
| notification_preference | NotificationPreference.java | created_by, deleted_at, updated_by | Yes |
| problem | Problem.java | - | Yes |
| project | Project.java | - | Yes |
| saved_report | SavedReport.java | - | Yes |
| service_request | ServiceRequest.java | - | Yes |
| sla_instance | SlaInstance.java | - | Yes |
| sla_policy | SlaPolicy.java | - | Yes |
| sprint | Sprint.java | deleted_at | Yes |
| sprint_burndown_snapshot | SprintBurndownSnapshot.java | created_by, deleted_at, updated_at, updated_by | Yes |
| workflow | Workflow.java | - | Yes |
| workflow_status | WorkflowStatus.java | deleted_at, org_id | Yes |
| workflow_transition | WorkflowTransition.java | deleted_at, org_id | Yes |

## Migrations with Non-Portable / Conditional DDL

| Migration | Pattern | Offset |
|---|---|---|
| V11__automation.sql | CREATE OR REPLACE TRIGGER | 2024 |
| V11__automation.sql | CREATE OR REPLACE TRIGGER | 2163 |
| V13__team.sql | CREATE OR REPLACE TRIGGER | 1057 |
| V1__base.sql | CREATE OR REPLACE TRIGGER | 762 |
| V2__identity.sql | CREATE OR REPLACE TRIGGER | 2421 |
| V2__identity.sql | CREATE OR REPLACE TRIGGER | 2546 |
| V2__identity.sql | CREATE OR REPLACE TRIGGER | 2663 |
| V3__incidents.sql | CREATE OR REPLACE TRIGGER | 3782 |
| V3__incidents.sql | CREATE OR REPLACE TRIGGER | 3907 |
| V3__incidents.sql | CREATE OR REPLACE TRIGGER | 4032 |
| V3__incidents.sql | CREATE OR REPLACE TRIGGER | 4157 |

## All BaseEntity Tables Mapped

- app_user (AppUser.java)
- automation_rule (AutomationRule.java)
- automation_run_log (AutomationRunLog.java)
- business_calendar (BusinessCalendar.java)
- catalog_item (CatalogItem.java)
- category (Category.java)
- change_approval (ChangeApproval.java)
- change_request (ChangeRequest.java)
- fulfillment_task (FulfillmentTask.java)
- incident (Incident.java)
- incident_attachment (IncidentAttachment.java)
- incident_comment (IncidentComment.java)
- incident_link (IncidentLink.java)
- incident_watcher (IncidentWatcher.java)
- issue (Issue.java)
- issue_comment (IssueComment.java)
- issue_link (IssueLink.java)
- issue_type (IssueType.java)
- kb_article (KbArticle.java)
- kb_article_version (KbArticleVersion.java)
- kb_feedback (KbFeedback.java)
- notification (Notification.java)
- notification_preference (NotificationPreference.java)
- priority (Priority.java)
- problem (Problem.java)
- project (Project.java)
- role (Role.java)
- saved_report (SavedReport.java)
- service_request (ServiceRequest.java)
- sla_instance (SlaInstance.java)
- sla_policy (SlaPolicy.java)
- sprint (Sprint.java)
- sprint_burndown_snapshot (SprintBurndownSnapshot.java)
- team (Team.java)
- user_role (UserRole.java)
- workflow (Workflow.java)
- workflow_status (WorkflowStatus.java)
- workflow_transition (WorkflowTransition.java)
