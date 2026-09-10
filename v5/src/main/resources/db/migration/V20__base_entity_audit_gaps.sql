-- Fix missing BaseEntity audit columns and updated_at triggers.
-- This migration is idempotent; it uses IF NOT EXISTS.

DROP TRIGGER IF EXISTS trg_business_calendar_updated_at ON business_calendar;
CREATE TRIGGER trg_business_calendar_updated_at
    BEFORE UPDATE ON business_calendar
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_catalog_item_updated_at ON catalog_item;
CREATE TRIGGER trg_catalog_item_updated_at
    BEFORE UPDATE ON catalog_item
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE change_approval
    ADD COLUMN IF NOT EXISTS org_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'::uuid;
DROP TRIGGER IF EXISTS trg_change_approval_updated_at ON change_approval;
CREATE TRIGGER trg_change_approval_updated_at
    BEFORE UPDATE ON change_approval
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_change_request_updated_at ON change_request;
CREATE TRIGGER trg_change_request_updated_at
    BEFORE UPDATE ON change_request
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE fulfillment_task
    ADD COLUMN IF NOT EXISTS org_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'::uuid;
DROP TRIGGER IF EXISTS trg_fulfillment_task_updated_at ON fulfillment_task;
CREATE TRIGGER trg_fulfillment_task_updated_at
    BEFORE UPDATE ON fulfillment_task
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_incident_attachment_updated_at ON incident_attachment;
CREATE TRIGGER trg_incident_attachment_updated_at
    BEFORE UPDATE ON incident_attachment
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_incident_link_updated_at ON incident_link;
CREATE TRIGGER trg_incident_link_updated_at
    BEFORE UPDATE ON incident_link
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_incident_watcher_updated_at ON incident_watcher;
CREATE TRIGGER trg_incident_watcher_updated_at
    BEFORE UPDATE ON incident_watcher
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_issue_updated_at ON issue;
CREATE TRIGGER trg_issue_updated_at
    BEFORE UPDATE ON issue
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE issue_comment
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
DROP TRIGGER IF EXISTS trg_issue_comment_updated_at ON issue_comment;
CREATE TRIGGER trg_issue_comment_updated_at
    BEFORE UPDATE ON issue_comment
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE issue_link
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_by UUID;
DROP TRIGGER IF EXISTS trg_issue_link_updated_at ON issue_link;
CREATE TRIGGER trg_issue_link_updated_at
    BEFORE UPDATE ON issue_link
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_issue_type_updated_at ON issue_type;
CREATE TRIGGER trg_issue_type_updated_at
    BEFORE UPDATE ON issue_type
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_kb_article_updated_at ON kb_article;
CREATE TRIGGER trg_kb_article_updated_at
    BEFORE UPDATE ON kb_article
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE kb_article_version
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS org_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'::uuid,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_by UUID;
DROP TRIGGER IF EXISTS trg_kb_article_version_updated_at ON kb_article_version;
CREATE TRIGGER trg_kb_article_version_updated_at
    BEFORE UPDATE ON kb_article_version
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE kb_feedback
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS org_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'::uuid,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_by UUID;
DROP TRIGGER IF EXISTS trg_kb_feedback_updated_at ON kb_feedback;
CREATE TRIGGER trg_kb_feedback_updated_at
    BEFORE UPDATE ON kb_feedback
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_notification_updated_at ON notification;
CREATE TRIGGER trg_notification_updated_at
    BEFORE UPDATE ON notification
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE notification_preference
    ADD COLUMN IF NOT EXISTS created_by UUID,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_by UUID;
DROP TRIGGER IF EXISTS trg_notification_preference_updated_at ON notification_preference;
CREATE TRIGGER trg_notification_preference_updated_at
    BEFORE UPDATE ON notification_preference
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_problem_updated_at ON problem;
CREATE TRIGGER trg_problem_updated_at
    BEFORE UPDATE ON problem
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_project_updated_at ON project;
CREATE TRIGGER trg_project_updated_at
    BEFORE UPDATE ON project
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_saved_report_updated_at ON saved_report;
CREATE TRIGGER trg_saved_report_updated_at
    BEFORE UPDATE ON saved_report
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_service_request_updated_at ON service_request;
CREATE TRIGGER trg_service_request_updated_at
    BEFORE UPDATE ON service_request
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_sla_instance_updated_at ON sla_instance;
CREATE TRIGGER trg_sla_instance_updated_at
    BEFORE UPDATE ON sla_instance
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_sla_policy_updated_at ON sla_policy;
CREATE TRIGGER trg_sla_policy_updated_at
    BEFORE UPDATE ON sla_policy
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE sprint
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
DROP TRIGGER IF EXISTS trg_sprint_updated_at ON sprint;
CREATE TRIGGER trg_sprint_updated_at
    BEFORE UPDATE ON sprint
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE sprint_burndown_snapshot
    ADD COLUMN IF NOT EXISTS created_by UUID,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_by UUID;
DROP TRIGGER IF EXISTS trg_sprint_burndown_snapshot_updated_at ON sprint_burndown_snapshot;
CREATE TRIGGER trg_sprint_burndown_snapshot_updated_at
    BEFORE UPDATE ON sprint_burndown_snapshot
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_workflow_updated_at ON workflow;
CREATE TRIGGER trg_workflow_updated_at
    BEFORE UPDATE ON workflow
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE workflow_status
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS org_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'::uuid;
DROP TRIGGER IF EXISTS trg_workflow_status_updated_at ON workflow_status;
CREATE TRIGGER trg_workflow_status_updated_at
    BEFORE UPDATE ON workflow_status
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE workflow_transition
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS org_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'::uuid;
DROP TRIGGER IF EXISTS trg_workflow_transition_updated_at ON workflow_transition;
CREATE TRIGGER trg_workflow_transition_updated_at
    BEFORE UPDATE ON workflow_transition
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

