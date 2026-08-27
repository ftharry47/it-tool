# Phase 3 — SLA Engine

## What was built

1. **Flyway migration** `V5__sla_engine.sql`
   - `business_calendar` (timezone, `working_hours` JSONB, `holidays` JSONB)
   - `sla_policy` (`applies_to`, `priority_filter`, `response_target_minutes`, `resolution_target_minutes`, `business_hours_calendar_id`)
   - `sla_instance` (`response_due_at`, `resolution_due_at`, `response_met_at`, `resolution_met_at`, `paused_at`, `total_paused_minutes`, `breach_status`)

2. **JPA entities**
   - `BusinessCalendar`
   - `SlaPolicy` (replaces the earlier `sla_definition`)
   - `SlaInstance` (replaces the earlier `incident_sla`)

3. **Business-hours-aware due date calculation**
   - `BusinessHoursCalculator` walks forward from `created_at`, skipping non-working days and holidays.
   - Reads `working_hours` and `holidays` from `business_calendar` JSONB.

4. **Pause/resume logic**
   - `SlaEngine.onStatusChanged` is invoked through the existing `IncidentStatusMachine` / `IncidentService.updateStatus` path.
   - `ON_HOLD` sets `paused_at` on the `sla_instance`.
   - Leaving `ON_HOLD` adds elapsed time to `total_paused_minutes` and extends both due dates by that pause.

5. **Breach monitoring job**
   - `SlaBreachMonitorJob` (Quartz, every 5 minutes) checks open `sla_instance` rows.
   - Computes `ON_TRACK` → `AT_RISK` (75% elapsed) → `BREACHED` (100%) and publishes `SlaBreachStatusChangedEvent` only on the transition.
   - Wired by `QuartzConfig` with a `SimpleScheduleBuilder` repeating every 5 minutes.

6. **REST endpoints**
   - `GET/POST/PUT/DELETE /api/v1/sla-policies` (admin)
   - `GET/POST/PUT/DELETE /api/v1/business-calendars` (admin)
   - `GET /api/v1/incidents/{id}/sla`

7. **Tests**
   - `BusinessHoursCalculatorTest` — weekend/holiday skip cases
   - `SlaEngineTest` — pause/resume, first response, resolution
   - `IncidentStatusMachineTest` updated for the Phase 3 `REOPENED` state

## Assumptions made

- SLA policies are matched by `applies_to = INCIDENT` and an optional `priority_filter` for the priority name.
- Paused minutes are added as raw calendar minutes to the due date and then walked through the linked business calendar.
- The Quartz job uses an in-memory trigger for now; the job store can be switched to JDBC in Phase 11 if persistent scheduling is required.

## How to verify

```powershell
.\package.ps1
```

Output: `target/app.zip`.

## Build result

- `mvn package` with tests enabled: **20 tests run, 0 failures, 0 errors, 1 skipped** (Testcontainers disabled due to no Docker)
- `BusinessHoursCalculatorTest`: 8 passed
- `SlaEngineTest`: 4 passed
- `IncidentStatusMachineTest`: 4 passed
- `IncidentV1ControllerTest`: 1 passed
