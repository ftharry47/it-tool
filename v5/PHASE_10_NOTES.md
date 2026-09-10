# Phase 10 — Reporting & Analytics

## Status

Completed. Full test suite: **101 tests, 0 failures, 0 errors, 1 skipped**. `target/app.zip` rebuilt cleanly.

## What was built

### Database

- `V17__reporting.sql` adds the `saved_report` table:
  - `id`, `org_id`, `name`, `entity`, `filters` (JSON), `group_by`, `date_range` (JSON), audit columns, `deleted_at`.

### Entities & repositories

- `SavedReport` entity and `SavedReportRepository`.

### Services

- `ReportingService`
  - `adHocQuery(orgId, request)` — generic, CriteriaBuilder-only query engine.
  - `ticketsSummary`, `slaCompliance`, `agentWorkload`, `sprintVelocity` — fixed dashboard counts.
- `SavedReportService` — create, list, get, update, soft-delete saved reports.

### Controller

- `ReportingController` under `/api/v1/reports` with class-level:
  ```
  @PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
  ```
  - All `/api/v1/reports/*` endpoints are AGENT+. No END_USER-visible report endpoints were added; the data (org-wide tickets, SLA, agent workload, sprint velocity) is agent/operational by nature.
  - Endpoints:
    - `GET /tickets-summary`
    - `GET /sla-compliance`
    - `GET /agent-workload`
    - `GET /sprint-velocity`
    - `POST /query`
    - `GET/POST/PATCH/DELETE /saved`

## Generic query engine — safety & guards

- **No SQL injection**: the `entity` is resolved from a hardcoded whitelist (`incident`, `issue`, `problem`, `change`, `service_request`). The `field` and `groupBy` values are checked against a per-entity field whitelist. No user input is ever concatenated into SQL.
- **CriteriaBuilder only**: all predicates, grouping, and selection use `jakarta.persistence.criteria` API with parameterized `Path` and `CriteriaBuilder` methods.
- **Tenant isolation**: every query adds `cb.equal(root.get("orgId"), orgId)`.
- **Operators limited**: `eq`, `ne`, `in` only (initial scope). Values are parsed to the declared Java type of the whitelisted field.
- **Query guards**:
  - Date range span is capped at **24 months**.
  - Result rows are capped with `query.setMaxResults(1000)`.
- **Saved reports** store the query definition (filters, groupBy, dateRange) as JSON, not pre-computed results.

## Tests

- `ReportingServiceTest`
  - `unknownEntityIsRejected`
  - `unknownFilterFieldIsRejected`
  - `dateRangeSpanGuardIsEnforced`
  - `groupedIncidentQueryUsesOrgIdPredicateAndRespectsRowLimit` — verifies `orgId` predicate, grouping, and the 1000-row `setMaxResults`.
- `ReportingControllerTest`
  - `agentCanAccessTicketsSummary`
  - `endUserCannotAccessReports`
  - `agentCanRunAdHocQuery`

## Dashboard data shape

- `GET /api/v1/reports/tickets-summary` returns `{ total, open, inProgress, resolvedToday }`.
- `GET /api/v1/reports/sla-compliance` returns `{ total, breached, compliancePercent }`.
- `GET /api/v1/reports/agent-workload` returns a list of `{ agentName, openCount }` where `agentName` is the assignee's display name (or email).
- `GET /api/v1/reports/sprint-velocity` returns a list of `{ sprintName, committed, completed }` where `committed` is the issue count per sprint and `completed` is the count of issues whose `WorkflowStatus.category` is `DONE`.

## Assumptions / next refinements

- `sprint-velocity` completed count is derived from `WorkflowStatus.Category.DONE` (terminal status). If your project uses a different terminal category/name, update `ReportingService.sprintVelocity` accordingly.
- Saved report CRUD stores raw definition JSON; it does not store or cache result sets.
