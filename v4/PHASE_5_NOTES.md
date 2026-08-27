# Phase 5 — Service Catalog + Request Management

## What was built

### Flyway `V8__service_catalog.sql`

- `catalog_item` with `form_schema` and `fulfillment_tasks` JSONB, plus `approval_required` and a single `approver_id`.
- `service_request` with `form_data` JSONB, status, `approval_required`, `approval_decision`, `approver_id`, and `needed_by`.
- `fulfillment_task` linked to `service_request`.

### Entities / Repositories

- `CatalogItem`, `ServiceRequest`, `FulfillmentTask`
- `CatalogItemRepository`, `ServiceRequestRepository`, `FulfillmentTaskRepository`

### Form validation

- `FormSchemaValidator` validates submitted `form_data` JSON against the `catalog_item.form_schema` JSON array.
- Supports required, type (string/number/boolean/select), and select option validation.

### Status workflow

- `ServiceRequestStatusMachine` status set:
  `SUBMITTED, PENDING_APPROVAL, APPROVED, REJECTED, IN_FULFILLMENT, FULFILLED, CANCELLED`
- Approval-required items: `SUBMITTED -> PENDING_APPROVAL -> APPROVED -> IN_FULFILLMENT -> FULFILLED`
- No-approval items: `SUBMITTED -> IN_FULFILLMENT -> FULFILLED`
- `CANCELLED` from any non-terminal state
- `FULFILLED` only allowed when all fulfillment tasks are completed

### Approval model

- Single approver per catalog item (`catalog_item.approver_id`).
- `POST /{id}/approve` and `POST /{id}/reject` record decision, comment, `decided_at`, and actual approver.
- No separate approval-chain table.

### Fulfillment task seeding

- `service_request` tasks are generated from `catalog_item.fulfillment_tasks` JSON template at the moment the request becomes `APPROVED` or `IN_FULFILLMENT` (for no-approval items).
- `POST /{id}/tasks/{taskId}/complete` marks a task complete; auto-advances to `FULFILLED` when the last task is completed.

### REST endpoints

Catalog items (admin):
- `GET/POST /api/v1/catalog-items`
- `GET/PATCH /api/v1/catalog-items/{id}`

Service requests:
- `GET/POST /api/v1/service-requests`
- `GET /api/v1/service-requests/{id}`
- `POST /api/v1/service-requests/{id}/submit`
- `POST /api/v1/service-requests/{id}/approve`
- `POST /api/v1/service-requests/{id}/reject`
- `PATCH /api/v1/service-requests/{id}/status`
- `POST /api/v1/service-requests/{id}/tasks/{taskId}/complete`

### Tests

- `FormSchemaValidatorTest` — valid, missing required, wrong type, invalid select
- `ServiceRequestStatusMachineTest` — approval-required path, no-approval path, blocked skip, fulfilled with/without completed tasks

## Build result

```powershell
.\package.ps1
```

- `mvn package` with tests: **43 tests run, 0 failures, 0 errors, 1 skipped**
- `FormSchemaValidatorTest: 4 passed`
- `ServiceRequestStatusMachineTest: 7 passed`
- `target/app.zip` built cleanly
