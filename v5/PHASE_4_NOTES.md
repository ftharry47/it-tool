# Phase 4 — Problem & Change Management

## What was built

### Problem Management

1. **Flyway migration** `V6__problem_management.sql`
   - `problem` (number, title, description, status, root_cause, workaround, assignee, resolved/closed timestamps)
   - `problem_incident_link` (composite PK)

2. **Entities / Repositories**
   - `Problem` (status enum: `NEW`, `INVESTIGATING`, `KNOWN_ERROR`, `RESOLVED`, `CLOSED`)
   - `ProblemIncidentLink` with an inner `ProblemIncidentLinkId` `@IdClass`
   - `ProblemRepository`, `ProblemIncidentLinkRepository`

3. **State machine** `ProblemStatusMachine`
   - Linear progression with `NEW -> INVESTIGATING -> KNOWN_ERROR -> RESOLVED -> CLOSED`, plus `RESOLVED -> INVESTIGATING` reopen path.

4. **Service & Controller**
   - `ProblemService` handles create/update, status transitions, link incident, list linked incidents.
   - `ProblemController` exposes:
     - `GET/POST /api/v1/problems`
     - `GET/PATCH /api/v1/problems/{id}`
     - `PATCH /api/v1/problems/{id}/status`
     - `POST /api/v1/problems/{id}/link-incident`
     - `GET /api/v1/problems/{id}/incidents`

5. **Tests**
   - `ProblemStatusMachineTest`

### Change Management

1. **Flyway migration** `V7__change_management.sql`
   - `change_request` (number, title, description, change_type, risk, status, planned_start/end, rollback_plan, PIR, linked_problem_id)
   - `change_approval` (sequence_order, status, decided_at, comment)

2. **Entities / Repositories**
   - `ChangeRequest` (`STANDARD`, `NORMAL`, `EMERGENCY`; risk `LOW/MEDIUM/HIGH`; status workflow)
   - `ChangeApproval` (`PENDING`, `APPROVED`, `REJECTED`)
   - `ChangeRequestRepository`, `ChangeApprovalRepository`

3. **State machine** `ChangeStatusMachine`
   - DRAFT → PENDING_APPROVAL → APPROVED → SCHEDULED → IN_PROGRESS → COMPLETED/FAILED/ROLLED_BACK, with REJECTED and CANCELLED paths.

4. **Workflow service**
   - `ChangeService`
     - Create/update changes
     - `submitForApproval`: `STANDARD` auto-approves; `NORMAL`/`EMERGENCY` move to `PENDING_APPROVAL`
     - Sequential approval for `NORMAL` changes
     - `EMERGENCY` can move to `IN_PROGRESS` after the first approval
     - `reject` marks the change `REJECTED`
     - `getCalendar` returns scheduled/in-progress changes and detects overlapping planned windows

5. **REST endpoints** `ChangeController`
   - `GET/POST /api/v1/changes`
   - `GET/PATCH /api/v1/changes/{id}`
   - `PATCH /api/v1/changes/{id}/status`
   - `POST /api/v1/changes/{id}/submit-for-approval`
   - `POST /api/v1/changes/{id}/approvals`
   - `POST /api/v1/changes/{id}/approve`
   - `POST /api/v1/changes/{id}/reject`
   - `GET /api/v1/changes/calendar`

6. **Tests**
   - `ChangeStatusMachineTest`

## Build result

```powershell
.\package.ps1
```

- `mvn package` with tests enabled: **28 tests run, 0 failures, 0 errors, 1 skipped** (Testcontainers disabled due to no Docker)
- Artifacts: `target/app.zip`, `target/app.jar`
