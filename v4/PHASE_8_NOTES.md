# Phase 8 — Automation Engine

## Status

- **Sub-step 4 — action handlers and condition evaluator: APPROVED** (team/assignment backfill, `SET_STATUS`, `ASSIGN_TO_USER/TEAM`, `CALL_WEBHOOK`, `ConditionEvaluator`, tests, and full build completed).
- **Sub-step 5 — dry-run endpoint: COMPLETED**.

## Schema

- `V11__automation.sql` adds `automation_rule` and `automation_run_log` tables.
- `automation_rule` stores `trigger_type`, `trigger_entity`, `trigger_config` (TEXT JSON), `conditions` (TEXT JSON), `actions` (TEXT JSON), and an `active` flag.
- `automation_run_log` stores every rule evaluation/execution with `status`, `output`, and `error`.

## Phase 8.1 — Rule CRUD

### Endpoints (`AutomationRuleController`)

| Method | Path | Authorization | Notes |
|--------|------|---------------|-------|
| POST   | `/api/v1/automation/rules` | `ADMIN` or `SUPER_ADMIN` | create rule |
| GET    | `/api/v1/automation/rules` | any authenticated user | list rules for org |
| GET    | `/api/v1/automation/rules/{id}` | any authenticated user | get one rule |
| PATCH  | `/api/v1/automation/rules/{id}` | `ADMIN` or `SUPER_ADMIN` | update rule |
| DELETE | `/api/v1/automation/rules/{id}` | `ADMIN` or `SUPER_ADMIN` | soft-delete rule |

Role authorization is enforced by `@PreAuthorize`. Rule creation and editing require `ADMIN` or `SUPER_ADMIN`.

## Phase 8.4 — New Action Handlers

### Implemented actions

- `SET_STATUS` (`StatusChangeHandler`) — routes through `IncidentService.updateStatus` (uses `IncidentStatusMachine`) and `IssueService.changeStatus` (uses `WorkflowTransitionValidator`) so illegal transitions are rejected the same way as direct API calls.
- `ASSIGN_TO_USER` / `ASSIGN_TO_TEAM` (`AssignHandler`) — reuses `IncidentService.assign` / `assignTeam` and `IssueService.assign` / `assignTeam`.
- `CALL_WEBHOOK` (`WebhookHandler` + `WebhookUrlValidator`) — JDK `HttpClient`, 5s connect / 10s response timeouts, async with `CompletableFuture.orTimeout`, blocklist for `localhost`, private (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `127.0.0.0/8`), and link-local ranges.
- `ConditionEvaluator` — real flat condition evaluation supporting `eq`, `ne`, `in`, `gt`, `gte`, `lt`, `lte` on event payload fields.

### Failure isolation

- `WebhookException` is caught per action in `AutomationActionExecutor`; other actions continue and a final `WebhookException` is thrown with the combined output.
- `AutomationRuleEngine` logs webhook failures with status `FAILED` and `output` showing which actions succeeded and which failed, while other rules and the triggering transaction continue.

### New tests

- `ConditionEvaluatorTest` — matching, non-matching, `in`, numeric, empty conditions.
- `StatusChangeHandlerTest` — automation `NEW -> CLOSED` propagates `IllegalStateException` from the state-machine-validated service.
- `WebhookUrlValidatorTest` — `localhost` and `192.168.x.x` blocked.
- `AutomationActionExecutorTest` — a `CALL_WEBHOOK` failure still lets `SET_FIELD` and `ADD_COMMENT` run.

## Phase 8.5 — Dry-Run Endpoint

### Endpoint (`AutomationRuleController`)

| Method | Path | Authorization | Notes |
|--------|------|---------------|-------|
| POST   | `/api/v1/automation/rules/{id}/test` | `AGENT`/`TEAM_LEAD`/`ADMIN`/`SUPER_ADMIN` | dry-run: evaluates rule conditions and returns actions that would execute, without running them |

### Service (`AutomationRuleTestService`)

- Loads the rule and checks the `active` flag.
- Runs `ConditionEvaluator` against the provided `samplePayload`.
- If conditions do **not** match or the rule is inactive, returns `matched: false` and an empty `actions` array.
- If conditions match, returns `matched: true` and the rule's parsed `actions` JSON so callers can see exactly what would run.
- Does not call `AutomationActionExecutor`, does not persist a run log, and does not modify any entity.

### New tests

- `AutomationRuleTestServiceTest` — matching, non-matching, and inactive rule scenarios.

## Phase 8 End-to-End Verification

### Test coverage

- `AutomationRuleEngineEndToEndTest`
  - `incidentCreationWithMatchingRuleExecutesMultipleActionsInOrderAndLogsOneRun` — an active `INCIDENT.CREATED` rule with `SET_FIELD`, `ADD_COMMENT`, and `SET_STATUS` executes all three actions in order and persists exactly one `automation_run_log` with `status = EXECUTED` and `output` containing `SET_FIELD: OK; ADD_COMMENT: OK; SET_STATUS: OK`.
  - `failingWebhookActionLogsCorrectlyAndDoesNotBlockOtherActions` — a rule with `ADD_COMMENT`, a blocked `CALL_WEBHOOK`, and `SET_FIELD` logs one `automation_run_log` with `status = FAILED`, `output` containing `ADD_COMMENT: OK; CALL_WEBHOOK: FAILED; SET_FIELD: OK`, and `error` describing the blocked webhook, while the other actions still complete.

### Engine improvements driven by end-to-end testing

- `AutomationActionExecutor.execute` now returns the joined action output string.
- `AutomationRuleEngine` now records the full action outcome in `AutomationRunLog.output` for both `EXECUTED` and `FAILED` runs instead of a generic "Actions executed" string.

### Build and approval

- Full Maven test suite: **88 tests run, 0 failures, 0 errors, 1 skipped** (`ItsPortalApplicationTests` skipped because Docker/Testcontainers is unavailable in this environment).
- `package.ps1` produced a clean `target/app.zip`.
- **Phase 8 status: APPROVED**.

## Assumptions / known limitations

- **CALL_WEBHOOK SSRF protection**: uses a pre-call `InetAddress` check for `localhost`, private, and link-local ranges. This is a simple defense and is **not** DNS-rebinding-resistant; acceptable for an internal tool but not a hardened SSRF barrier.
- `ASSIGN_TO_TEAM` required backfilling the Phase 1 `team` / `team_member` schema and wiring `assignment_team_id` on `incident` and `issue`.
