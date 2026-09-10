# Phase 1 Notes — Identity, Access, and Team Gaps

## Audit Summary (sub-step 4 blocker)

While wiring `ASSIGN_TO_TEAM` for the Phase 8 automation engine, the following Phase 1 (Section 4) spec gaps were discovered:

- **`team` / `team_member` tables** — missing entirely.
- **`app_user.assignment_team_id`** on `incident` and `issue` — not in schema or entities.
- **`manager_id` hierarchy** — not implemented on `app_user`.
- **`mfa_enabled` field** — not present on `app_user`.
- **`is_active` boolean / deactivate flow** — `app_user` only has a `status` string enum (`ACTIVE`, `INACTIVE`, `SUSPENDED`).
- **`audit_log` table** — not present; `PATCH /users/{id}/role` cannot be writing audit rows as originally required.

## Implemented Gap Fixes

- `V13__team.sql` adds `team` and `team_member` tables, with triggers.
- `V14__assignment_team.sql` adds `assignment_team_id` FK to `incident` and `issue`.
- `Team`, `TeamMember` (composite PK via `TeamMemberId`), `TeamRepository`, `TeamMemberRepository`.
- `TeamService` with create / list / get / member add/remove.
- `TeamController` at `GET/POST /api/v1/teams` (POST requires ADMIN or SUPER_ADMIN).
- `IncidentService.assignTeam(...)` and `IssueService.assignTeam(...)` to set `assignment_team_id` through existing service methods.
- `V15__audit_log.sql` adds `audit_log` table with `before_state`/`after_state` JSONB.
- `AuditLog` entity, `AuditLogRepository`, `AuditLogService`, `AuditLogController` at `GET /api/v1/audit-log` (ADMIN/SUPER_ADMIN).
- `UserService.updateRole(...)` now writes an `UPDATE_ROLE` audit row with before/after role arrays.
- `AdminController` `PATCH /api/admin/users/{id}/role` captures actor and IP and persists the audit row.

## Confirmed missing / deferred to Phase 11 hardening

- `manager_id` hierarchy on `app_user` — no column or endpoint exists.
- `mfa_enabled` field/flow — not present on `app_user` or anywhere in the codebase.
- `is_active` boolean / deactivate flow — genuinely absent. `app_user` only has `status` (ACTIVE/INACTIVE/SUSPENDED); there is no `PATCH /users/{id}/deactivate` or `is_active` column. Verified by searching the codebase for `deactivate` and `is_active`.

## Remaining Phase 1 Gaps

None; the immediate compliance-relevant gaps have been addressed. The three identity hardening items above are recorded here for Phase 11.
