# Phase 7 — Project Management

## 7a — Foundation: projects, issue types, workflow engine

### Schema `V10a__project_workflow.sql`

- `project` (org, key, name, lead, status)
- `issue_type` (per-org)
- `workflow` (org/project, name, description)
- `workflow_status` (workflow, name, category: BACKLOG/TODO/IN_PROGRESS/DONE, display order, terminal)
- `workflow_transition` (workflow, from_status_id, to_status_id, screen)

### Entities / Repos

- `Project`, `IssueType`, `Workflow`, `WorkflowStatus`, `WorkflowTransition`
- `ProjectRepository`, `IssueTypeRepository`, `WorkflowRepository`, `WorkflowStatusRepository`, `WorkflowTransitionRepository`

### Workflow validation

- `WorkflowTransitionValidator` checks `workflow_transition` for `(from_status_id, to_status_id)`.
- Throws `IllegalStateException` on illegal transitions, mirrors the status-machine pattern used in Incident/Problem/Change.
- `WorkflowTransitionValidatorTest` proves legal transitions pass and illegal transitions are rejected.

### CRUD + auth

- `ProjectController` (`/api/v1/projects`)
- `IssueTypeController` (`/api/v1/issue-types`)
- `WorkflowController` (`/api/v1/workflows` plus `/{id}/statuses` and `/{id}/transitions`)
- Create/update/delete restricted to `TEAM_LEAD`, `ADMIN`, `SUPER_ADMIN`.
- Read endpoints are `isAuthenticated()`.

## 7b — Issues, sprints, board, burndown

### Schema `V10b__issues_sprints.sql`

- `sprint` (project, name, goal, status: PLANNING/ACTIVE/COMPLETED, dates, completed_at)
- `issue` (project, issue_type, workflow, workflow_status, sprint_id, epic_id, parent_issue_id, number, key, summary, description, assignee, reporter, story_points, remaining_points, priority)
- `issue_link` (from_issue_id, to_issue_id, link_type)
- `issue_comment` (issue_id, body)
- `sprint_burndown_snapshot` (sprint, snapshot_date, total_points, remaining_points, open_issues)

### Issue endpoints (IssueController)

- `POST /api/v1/issues` — create with `sprint_id`, `epic_id`, `parent_issue_id`, `assignee_id`, `reporter_id`, `story_points`, `priority`
- `GET /api/v1/issues/project/{projectId}` — list issues for a project
- `GET /api/v1/issues/project/{projectId}/backlog` — `sprint_id IS NULL`
- `GET /api/v1/issues/sprint/{sprintId}` — list issues in a sprint
- `GET /api/v1/issues/sprint/{sprintId}/board` — sprint (Scrum) board grouped by `WorkflowStatus`
- `GET /api/v1/issues/{id}` — single issue with all FKs
- `PATCH /api/v1/issues/{id}` — update `sprint_id`, `epic_id`, `parent_issue_id`, `assignee_id`, `story_points`
- `POST /api/v1/issues/{id}/status` — drag-and-drop status change (validated)
- `DELETE /api/v1/issues/{id}`

### Issue comments (IssueCommentController)

- `GET /api/v1/issues/{issueId}/comments`
- `POST /api/v1/issues/{issueId}/comments`

### Issue links (IssueLinkController)

- `GET /api/v1/issues/{issueId}/links`
- `POST /api/v1/issues/{issueId}/links`
- `DELETE /api/v1/issues/{issueId}/links/{linkId}`

### Project board (ProjectController)

- `GET /api/v1/projects/{key}/board?workflowId=...&sprintId=...`
  - No `sprintId` → Kanban board across the whole project
  - With `sprintId` → Scrum board scoped to that active sprint

### Core behavior

- **Backlog**: `issue.sprint_id IS NULL`.
- **Board**: groups by `WorkflowStatus` for a given `workflowId`; can filter to a `sprintId`.
- **Drag-and-drop**: `IssueService.changeStatus()` calls `WorkflowTransitionValidator.validate()` before persisting the new `workflow_status_id`. Illegal moves throw `IllegalStateException`.
- **Sprint start**: sets `sprint.status = ACTIVE` and start date.
- **Sprint complete**: requires `SprintCompleteRequest.Destination` (`BACKLOG` or `NEXT_SPRINT`).
  - `BACKLOG` clears `sprint_id` on incomplete issues.
  - `NEXT_SPRINT` requires `nextSprintId` and moves incomplete issues there.
  - Done (`workflow_status.category = DONE`) issues keep their `sprint_id` unchanged.

### Issue numbering

- Each project gets its own database sequence: `issue_number_<project_id>` (UUID dashes replaced with underscores).
- `IssueService.nextNumber()` creates the sequence if it does not exist and then calls `nextval` for that project only.
- `nextval` is atomic, so concurrent issue creations within the same project get distinct numbers.
- Every project starts at 1, so `PROJ-1`, `PROJ-2`, `PROJ-3...` and a second project's issues are `OTHERPROJ-1`, `OTHERPROJ-2`, etc.
- The `key` is built as `project.key + "-" + number`, e.g. `PROJ-42`.

### Burndown

- `BurndownSnapshotJob` runs daily (Quartz, 24h interval) like `SlaBreachMonitorJob`.
- `QuartzConfig` registers both `JobDetail` and `Trigger` for `BurndownSnapshotJob`.
- The job calls `BurndownService.takeSnapshot()` for every `ACTIVE` sprint, pre-computing `total_points`, `remaining_points`, `open_issues`.
- `GET /api/v1/projects/{projectId}/sprints/{id}/burndown` returns pre-computed `sprint_burndown_snapshot` rows; no recalculation.

### Tests

- `WorkflowTransitionValidatorTest` — legal/illegal transitions
- `IssueServiceTest`:
  - `illegalDragAndDropStatusChangeIsRejected`
  - `numberingUsesSequenceAndProducesDistinctKeys` — two creates get different numbers
  - `createWithSprintEpicParentAssigneePersistsRelationships`
  - `backlogExcludesSprintIssues`
  - `boardByProjectGroupsByWorkflowStatus`
- `IssueCommentServiceTest` — create and list comments
- `IssueLinkServiceTest` — create links for every link type, list incoming + outgoing
- `SprintServiceTest`:
  - `completeSprintMovesIncompleteIssuesToBacklogAndKeepsDoneIssues`
  - `completeSprintMovesIncompleteIssuesToNextSprintAndKeepsDoneIssues`
- `BurndownServiceTest`:
  - `takeSnapshotSavesPrecomputedMetrics`
  - `getBurndownReturnsPrecomputedSnapshots`

## Build result

```powershell
.\package.ps1
```

```text
Tests run: 68, Failures: 0, Errors: 0, Skipped: 1
target/app.zip created
```
