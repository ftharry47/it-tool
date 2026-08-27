# Phase 12 — Frontend Completion

## What this phase is

The backend for Phases 4–10 is built and tested, but the frontend is currently only:
- `Login.tsx` — Microsoft sign-in
- `Dashboard.tsx` — simple landing / navigation
- `Incidents.tsx` — list + create incident
- `App.tsx` — routes to only `/`, `/login`, `/incidents`, and `/dashboard`

This phase is the missing frontend work for every module except Incident Management.

## 12.0 — Shared scaffolding (completed)

### Build result

- `npm run build` in `src/main/frontend` passed.
- `package.ps1` passed: **105 tests, 0 failures, 0 errors, 1 skipped**.
- `target/app.zip` rebuilt cleanly.

### What was built

- `src/main/frontend/src/auth/AuthProvider.tsx` — loads `/api/auth/me`, provides `currentUser` and `roles`.
- `src/main/frontend/src/routes/index.tsx` — role-based `useRoutes` with `getRouteDefinitions(role)`.
- `src/main/frontend/src/routes/utils.ts` — `highestRole()` and `getDefaultRoute()`.
- `src/main/frontend/src/components/layout/AppLayout.tsx` — sidebar + `<Outlet>`.
- `src/main/frontend/src/components/layout/RoleNav.tsx` — renders only the current role's route tree.
- `src/main/frontend/src/components/layout/NavLink.tsx` — active-state nav link.
- `src/main/frontend/src/components/ui/ComingSoon.tsx` — placeholder for not-yet-built modules.
- `src/main/frontend/src/components/ui/Loading.tsx`, `ErrorFallback.tsx`, `StatusBadge.tsx`, `DataTable.tsx`, `FormDrawer.tsx`, `EntityForm.tsx`.
- `src/main/frontend/src/pages/shared/Login.tsx`, `NotFound.tsx`, `Incidents.tsx`.
- `src/main/frontend/src/pages/dashboard/Dashboard.tsx`.
- `src/main/frontend/src/pages/home/Home.tsx`.
- `src/main/frontend/src/pages/admin/AdminHome.tsx`.
- `App.tsx` now wraps `AuthProvider` and `AppRoutes`.

### Route tree (role-based exclusion)

A role's route tree **only contains routes it can access**. Manual tampering to a disallowed path renders `NotFound`; the backend is still the real security boundary and returns 403 for unauthorized API calls.

- **Unauthenticated**: `/login`
- **END_USER** (`/home/*`):
  - `/home` → Home
  - `/home/incidents` → My Incidents
  - `/home/catalog` → Service Catalog (ComingSoon)
  - `/home/kb` → Knowledge Base (ComingSoon)
- **AGENT+** (`/dashboard/*`):
  - `/dashboard` → Dashboard
  - `/dashboard/incidents` → Incidents
  - `/dashboard/problems` → Problems (ComingSoon)
  - `/dashboard/changes` → Changes (ComingSoon)
  - `/dashboard/service-requests` → Service Requests (ComingSoon)
  - `/dashboard/projects` → Projects (ComingSoon)
  - `/dashboard/reports` → Reports (ComingSoon)
- **ADMIN/SUPER_ADMIN** (`/admin/*` plus dashboard routes):
  - All `/dashboard/*` routes above
  - `/admin` → Admin
  - `/admin/users` → User Admin (ComingSoon)
  - `/admin/catalog` → Catalog Admin (ComingSoon)
  - `/admin/workflows` → Workflow Admin (ComingSoon)
  - `/admin/automation` → Automation Rules (ComingSoon)

### Reusable components added

- `DataTable<T>` — generic sortable-ready table with loading, empty, and caption support.
- `StatusBadge` — status colors for `NEW`, `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, etc.
- `FormDrawer` — side-panel form shell.
- `EntityForm` — generic text/textarea/select form engine.
- `Loading` and `ErrorFallback`.

## 12.1 — Problem Management UI (completed)

### Build result

- `npm run build` passed.
- `package.ps1` passed: **105 tests, 0 failures, 0 errors, 1 skipped**.
- `target/app.zip` rebuilt cleanly.

### What was built

- `src/main/frontend/src/pages/dashboard/ProblemList.tsx` — list view using `DataTable<T>` and `StatusBadge`.
- `src/main/frontend/src/pages/dashboard/ProblemDetail.tsx` — detail/edit view, status transitions, linked-incident picker.
- `src/main/frontend/src/components/ui/StatusBadge.tsx` — extended status map to cover Problem (`INVESTIGATING`, `KNOWN_ERROR`), Change (`PENDING_APPROVAL`, `APPROVED`, `SCHEDULED`, `COMPLETED`, `FAILED`, `ROLLED_BACK`, `REJECTED`, `CANCELLED`, `DRAFT`), Service Request (`SUBMITTED`, `IN_FULFILLMENT`, `FULFILLED`), Knowledge Base (`PENDING_REVIEW`), and workflow statuses (`BACKLOG`, `TODO`, `DONE`).
- `routes/index.tsx` updated with `/dashboard/problems` and `/dashboard/problems/:id`.

### Views

- **Problem list**: columns for `Number`, `Title`, `Status` (badge), `Root Cause`; title links to detail.
- **Problem detail**:
  - Editable `EntityForm` for `title`, `description`, `rootCause`, `workaround` with `PATCH /api/v1/problems/{id}`.
  - Status transition section with a `<select>` of legal next statuses for the current state and a `Transition` button that calls `PATCH /api/v1/problems/{id}/status`.
  - `FormDrawer` with `EntityForm` to search/select an incident and call `POST /api/v1/problems/{id}/link-incident`.
  - `DataTable` of linked incidents with `StatusBadge`.

## 12.3 — Service Catalog + Requests UI (completed)

### Build result

- `npm run build` passed.
- `package.ps1` passed: **105 tests, 0 failures, 0 errors, 1 skipped**.
- `target/app.zip` rebuilt cleanly.

### What was built

- `src/main/frontend/src/components/ui/EntityForm.tsx` — extended with `number` and `boolean` field types.
- `src/main/frontend/src/components/ui/SchemaForm.tsx` — adapter that turns a catalog `form_schema` JSON array into an `EntityForm` with client-side UX validation; the backend `FormSchemaValidator` remains the real gate.
- `src/main/frontend/src/pages/home/CatalogBrowse.tsx` — END_USER catalog browse and dynamic form submission.
- `src/main/frontend/src/pages/dashboard/ServiceRequestList.tsx` and `ServiceRequestDetail.tsx` — AGENT list, detail, approve/reject, fulfillment task completion.
- `src/main/frontend/src/pages/admin/CatalogAdmin.tsx` — catalog item CRUD with JSON `form_schema` editor and live `SchemaForm` preview.
- `src/main/frontend/src/routes/index.tsx` — `/home/catalog`, `/dashboard/service-requests`, `/dashboard/service-requests/:id`, `/admin/catalog` wired.

### END_USER flow

`/home/catalog` shows active catalog items as cards. Clicking **Request** opens a `FormDrawer` with a `SchemaForm` rendered from the item's `form_schema`. Client-side validation gives immediate feedback; the final `POST /api/v1/service-requests` is validated by the backend and any server rejection is shown clearly (not swallowed).

### AGENT flow

`/dashboard/service-requests` lists requests with `DataTable` and `StatusBadge`. The detail view shows request data, approval state, and a `DataTable` of fulfillment tasks. Agents can approve/reject (with comment via `FormDrawer`) and complete tasks.

### ADMIN flow

`/admin/catalog` lists catalog items. The edit/create drawer has `EntityForm` fields for name, description, category, etc., and JSON textareas for `form_schema` and `fulfillment_tasks`. A live `SchemaForm` preview renders the current `form_schema`. Malformed JSON or an invalid field shape stops the preview with an inline error and also blocks save, so a broken schema cannot cascade to the END_USER experience.

## 12.4 — Knowledge Base UI (completed)

### Build result

- `npm run build` passed after adding `react-markdown`.
- `package.ps1` passed: **105 tests, 0 failures, 0 errors, 1 skipped**.
- `target/app.zip` rebuilt cleanly.

### What was built

- `src/main/frontend/package.json` — added `react-markdown` dependency.
- `src/main/frontend/src/pages/home/KbBrowse.tsx` — END_USER browse/search of published articles.
- `src/main/frontend/src/pages/home/KbArticleView.tsx` — article view with markdown render and helpful/not-helpful feedback.
- `src/main/frontend/src/pages/dashboard/KbArticleList.tsx` — AGENT+ list with status filter and search.
- `src/main/frontend/src/pages/dashboard/KbArticleEditor.tsx` — create/edit with status transitions, version-snapshot notice, and preview.
- `src/main/frontend/src/pages/dashboard/KbVersionHistory.tsx` — collapsible version history.
- `src/main/frontend/src/routes/index.tsx` — wired `/home/kb`, `/home/kb/:id`, `/dashboard/kb`, `/dashboard/kb/:id`.

### Verification: PENDING_REVIEW -> DRAFT

`KbStatusMachine.java` confirms `PENDING_REVIEW` supports both `PUBLISHED` and `DRAFT`, so the UI's status `<select>` correctly offers the rework transition and the backend will accept it.

### END_USER flow

`/home/kb` lists `PUBLISHED` articles, with search via `GET /api/v1/kb/search`. Clicking an article opens `/home/kb/:id` where `react-markdown` renders the body. Users can give thumbs-up/thumbs-down feedback.

### AGENT+ flow

`/dashboard/kb` lists all articles by status. The editor (`/dashboard/kb/:id`) has title, category, and markdown body fields; a status `<select>` with only legal transitions (no `PUBLISHED -> DRAFT`); a **Show Preview** button; and a **Version History** panel. When an author edits an article whose current status is `PUBLISHED`, a yellow notice explains that the current version will be snapshotted before saving.

### Suggest-articles follow-up (deferred)

`GET /api/v1/kb/suggest?description=...` is not wired in 12.4. It is intended for live suggestions while a user types an incident description, so it is flagged as a follow-up against `Incidents.tsx` rather than the KB UI. No KB code was added for this endpoint.

## 12.2 — Change Management UI (completed)

### Build result

- `npm run build` passed.
- `package.ps1` passed: **105 tests, 0 failures, 0 errors, 1 skipped**.
- `target/app.zip` rebuilt cleanly.

### What was built

- `src/main/frontend/src/pages/dashboard/ChangeList.tsx` — list view with status filter.
- `src/main/frontend/src/pages/dashboard/ChangeDetail.tsx` — create/edit/detail with status transitions, approval stepper, PIR guard, and approver management.
- `src/main/frontend/src/pages/dashboard/ChangeCalendar.tsx` — custom CSS week-timeline with conflict highlighting.
- `src/main/frontend/src/components/ui/StatusBadge.tsx` — added `PENDING` style for change approvals.
- `src/main/frontend/src/routes/index.tsx` — wired `/dashboard/changes`, `/dashboard/changes/calendar`, `/dashboard/changes/:id`.

### List view

`DataTable` shows change number, title, type, risk, `StatusBadge`, and planned dates. A status filter and a **New Change** button are above the table. A **Calendar** button opens the week timeline.

### Detail view

- `EntityForm` for title, description, change type, risk, planned dates (text inputs for ISO date-time), rollback plan, linked problem, and EMERGENCY-only post-implementation review.
- Status transition `<select>` with only legal next statuses from `ChangeStatusMachine`. `CANCELLED` is reachable from any non-terminal state.
- **Submit for Approval** button for `DRAFT` changes.
- Approval panel:
  - `STANDARD` shows an **Auto-approved (Standard)** badge.
  - `NORMAL` shows a sequential stepper with the current pending approver highlighted. Each pending step has an **Act** button to open the Approve/Reject drawer. An **Add approver** row is available for `DRAFT` and `PENDING_APPROVAL`.
  - `EMERGENCY` notes the expedited workflow and any single approval moves the change to `IN_PROGRESS`.

### EMERGENCY PIR guard

When a change is `EMERGENCY` and the `postImplementationReview` field is blank, the `COMPLETED` option in the status `<select>` is disabled and a yellow banner explains: **“EMERGENCY change — Post-Implementation Review required. Marking this change COMPLETED is blocked until the Post-Implementation Review field is filled.”** Selecting `COMPLETED` also shows a clear error and does not call the backend.

### Calendar with conflict highlighting

`/dashboard/changes/calendar` shows a 7-day week. Each `SCHEDULED`/`IN_PROGRESS` change is a horizontal bar positioned by `plannedStart` and `plannedEnd`. Any item whose `id` appears in the backend `conflicts` array gets a red border. A red warning box lists each overlapping pair. Users can navigate previous/next weeks.

## 12.7 — Reporting Dashboards UI (completed)

### Build result

- `npm run build` passed after using `recharts`.
- `package.ps1` passed: **105 tests, 0 failures, 0 errors, 1 skipped**.
- `target/app.zip` rebuilt cleanly.
- Fixed `ItsPortalApplication.java` to call `ApplicationInsights.attach()` (the correct class in `applicationinsights-runtime-attach:3.4.19`) instead of the non-existent `RuntimeAttach.attach()`.

### What was built

- `src/main/java/com/alignedcardio/itsm/api/reporting/ReportMetadataResponse.java`
- `src/main/java/com/alignedcardio/itsm/service/reporting/ReportingService.java` — added `getMetadata()` returning real whitelists.
- `src/main/java/com/alignedcardio/itsm/api/reporting/ReportingController.java` — added `GET /api/v1/reports/metadata`.
- `src/main/frontend/src/pages/dashboard/ReportsDashboard.tsx` — four fixed dashboards with `recharts` visualizations.
- `src/main/frontend/src/pages/dashboard/SavedReportList.tsx` — saved report list, create/edit, run.
- `src/main/frontend/src/pages/dashboard/AdHocQueryBuilder.tsx` — query builder driven by metadata.
- `src/main/frontend/src/routes/index.tsx` — wired `/dashboard/reports`, `/dashboard/reports/saved`, `/dashboard/reports/query`.

### Metadata endpoint

`GET /api/v1/reports/metadata` returns the exact whitelists from `ReportingService`:

- `entities`: `change`, `incident`, `issue`, `problem`, `service_request`
- `fieldsByEntity`: the same fields that `ReportingService.FIELDS_BY_ENTITY` enforces
- `operators`: the real `ALLOWED_OPS` set — `eq`, `ne`, `in`
- `dateFieldByEntity`: the per-entity date field (all `createdAt`)

The UI uses this endpoint to populate the entity, groupBy, field, and operator dropdowns in the query builder and saved report editor. This avoids 400/403 rejections for disallowed entities, fields, or operators.

### Fixed dashboards

- **Tickets Summary**: cards + `PieChart` of total/open/in-progress/resolved today/closed.
- **SLA Compliance**: cards + `PieChart` of compliant vs. breached.
- **Agent Workload**: `BarChart` of open incident count by agent.
- **Sprint Velocity**: grouped `BarChart` of committed vs. completed by sprint.

All four use `recharts` inside `ResponsiveContainer`.

### Saved reports

`/dashboard/reports/saved` lists reports, supports create/update, delete, and run. The run action calls `POST /api/v1/reports/query` with the saved definition and renders results as a `BarChart` (when `groupBy` is set) or `DataTable` (ungrouped).

### Ad-hoc query builder

`/dashboard/reports/query` fetches metadata first. The entity dropdown only shows whitelisted entities; selecting an entity enables only that entity's fields for `groupBy` and filters; the operator dropdown only shows the backend-supported operators. Users can add multiple filters, choose a date range, and run the query. Results render as a chart or table.

### 409 stale-transition handling

When `ProblemDetail` submits an illegal or stale transition and the backend returns `409`:
- The user sees: **“That status transition is not allowed for this problem.”** (not a raw 409 or stack trace).
- `queryClient.invalidateQueries` and `queryClient.refetchQueries` run for `['problem', id]` so the user immediately sees the current problem state and can choose a valid next action.

## 1. How the frontend gap happened

- Every `PHASE_*_NOTES.md` file (0 through 10) describes backend work: migrations, entities, services, state machines, REST controllers, tests.
- Searches of the phase notes for `frontend`, `UI`, `React`, or `pages` return **zero matches**.
- Each phase's sign-off was based on `mvn package` passing and `target/app.zip` building cleanly, not on a usable UI.
- **Conclusion:** This was a slow drift across many phases, not a one-time decision. The prompts consistently treated backend implementation + passing tests as the definition of "done" and never pulled the frontend through.

## 2. Sub-phase plan

Order is chosen to maximize value early and reuse `Incidents.tsx` patterns.

### 12.0 — Shared scaffolding (M)

- Extend `App.tsx` routing and `Dashboard.tsx` navigation.
- Add a role-aware sidebar or top nav that conditionally shows admin/agent/end-user menus.
- Create reusable `DataTable`, `StatusBadge`, `FormDrawer`, and `EntityForm` wrappers.
- Standardize `react-query` hooks and `fetchWithToken` patterns for the new modules.
- Wire basic access control (hide/disable based on `CurrentUser.roles`).

### 12.1 — Problem Management (S)

- `ProblemList.tsx` — paginated table of problems.
- `ProblemDetail.tsx` — view, edit, status transition, linked incidents.
- `LinkIncidentModal.tsx` — search and attach incidents to a problem.
- Small: state machine is simple linear flow; UI mirrors Incidents.

### 12.2 — Change Management (M)

- `ChangeList.tsx` — list change requests.
- `ChangeDetail.tsx` — view, edit, status, risk, planned window.
- `ChangeApprovals.tsx` — list pending approvals, approve/reject.
- `ChangeCalendar.tsx` — read-only view of `GET /api/v1/changes/calendar`.
- Medium: more fields and a calendar view, but still CRUD + status.

### 12.3 — Service Catalog + Service Requests (M)

- `CatalogAdmin.tsx` — create/edit catalog items and JSON form schemas (admin only).
- `ServiceCatalog.tsx` — end-user browse/submit requests.
- `ServiceRequestList.tsx` — agent/approver view.
- `ServiceRequestDetail.tsx` — status, approval, fulfillment task checklist.
- Medium: form schema rendering is the new complexity; tasks are a checklist.

### 12.4 — Knowledge Base (M)

- `KbArticleList.tsx` — search, filter by category, status.
- `KbArticleView.tsx` — render markdown body, helpful/not-helpful buttons.
- `KbArticleEditor.tsx` — create/edit with status and versioning.
- `KbSearch.tsx` — debounced search against `/api/v1/kb/search`.
- Medium: markdown rendering and search are the main new pieces.

### 12.5 — Project Management (L)

- `ProjectList.tsx` — list projects and create new ones.
- `ProjectBoard.tsx` — Kanban/Scrum board grouping by `WorkflowStatus`.
- `IssueDetail.tsx` — create/edit issue, assign, story points, links, comments.
- `SprintList.tsx` — sprints for a project, start/complete.
- `SprintBurndown.tsx` — chart of `GET /api/v1/projects/{id}/sprints/{id}/burndown`.
- Large: drag-and-drop board, issue detail with many relationships, burndown chart.

### 12.6 — Automation Engine (L)

- `AutomationRuleList.tsx` — list rules, toggle active.
- `AutomationRuleEditor.tsx` — JSON-ish editor for trigger, conditions, actions.
- `AutomationRuleTest.tsx` — dry-run UI for `POST /api/v1/automation/rules/{id}/test`.
- `AutomationRunLog.tsx` — view execution logs.
- Large: the rule JSON editing is the hard part; may benefit from a structured builder later.

### 12.7 — Reporting Dashboards (M)

- `ReportingDashboard.tsx` — cards for `tickets-summary`, `sla-compliance`, `agent-workload`, `sprint-velocity`.
- `SavedReports.tsx` — list, create, run saved ad-hoc reports.
- `AdHocQueryBuilder.tsx` — filters, group-by, date range for whitelisted entities.
- Medium: mostly charts/cards and a simple query form.

### 12.8 — Notifications preferences (S)

- `NotificationPreferences.tsx` — in-app/email toggles and digest mode.
- Optional: a small in-app notification bell using the existing `/ws` STOMP endpoint.
- Small: mostly a settings form.

## 3. Sizing and sequencing

| Sub-phase | Size | Approx. effort relative to Incidents.tsx |
|-----------|------|------------------------------------------|
| 12.0 Shared scaffolding | M | 1x |
| 12.1 Problem Management | S | 0.7x |
| 12.2 Change Management | M | 1.2x |
| 12.3 Service Catalog/Requests | M | 1.3x |
| 12.4 Knowledge Base | M | 1.1x |
| 12.5 Project Management | L | 2.5x |
| 12.6 Automation Engine | L | 2.0x |
| 12.7 Reporting Dashboards | M | 1.2x |
| 12.8 Notification Preferences | S | 0.5x |

**Recommended order:** 12.0 → 12.1 → 12.3 → 12.4 → 12.2 → 12.7 → 12.5 → 12.6 → 12.8

This gives end-user and agent value (Problem, Service Catalog, KB) before the heavy admin screens (Project, Automation).

## 4. What is already fixed

- `package.ps1` no longer runs `mvn clean`; it removes only the final `app.jar`/`app.zip` artifacts and then runs `mvn package`, which avoids the `target/node/node_modules` Windows lock.
- `package.ps1` successfully produced `target/app.zip` in the latest run: **105 tests, 0 failures, 0 errors, 1 skipped**.

## 5. When the build is truly "complete"

The current `target/app.zip` is deployable for **incident management only**.

To call the whole product complete, the following must still happen:
- Build the frontend screens in 12.1–12.8.
- Run the Playwright E2E suite against a real Postgres backend with the `e2e-fixed-auth` profile.
- Run the load test from `scripts/load_test.ps1` against real Postgres.
- Perform the manual Microsoft login, keyboard, screen-reader, and Kudu checklists from `PHASE_11_NOTES.md`.

This is the boundary between the hardening work in Phase 11 and the new feature-completion work in Phase 12.
