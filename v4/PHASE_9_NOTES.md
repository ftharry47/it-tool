# Phase 9 — Notifications + Real-time

## Status

Phase 9 implementation complete and approved. Full test suite passes and `target/app.zip` builds cleanly.

## Schema

- `V16__notification.sql` adds:
  - `notification` table — persisted in-app/email messages with `channel`, `in_app_status`, `email_status`, `entity_type`, `entity_id`.
  - `notification_preference` table — per-user `in_app_enabled`, `email_enabled`, `email_address`, and `digest_mode` (`NONE`/`HOURLY`/`DAILY`).

## Core components

### `NotificationService`

- `send(NotificationRequest)` persists a `Notification` row and routes delivery based on `NotificationPreference`.
- In-app delivery uses `SimpMessagingTemplate.convertAndSendToUser(userId, "/queue/notifications", payload)`.
- Immediate email uses `JavaMailSender` when `digest_mode = NONE`.
- Digest mode leaves `email_status = PENDING` for the Quartz digest job.

### `NotificationHandler` + `SEND_NOTIFICATION`

- New action type `SEND_NOTIFICATION` added to `AutomationActionExecutor`.
- `NotificationHandler` reads `userId`, `subject`, `body`, and `channel` from the action JSON and calls `NotificationService.send`.
- The handler is wired into the automation action executor alongside `SET_FIELD`, `ADD_COMMENT`, `SET_STATUS`, `ASSIGN_*`, and `CALL_WEBHOOK`.

### WebSocket configuration

- `WebSocketConfig` enables STOMP/SockJS:
  - Endpoint: `/ws` with SockJS fallback.
  - Simple broker prefixes: `/topic`, `/queue`.
  - User destination prefix: `/user`.
  - Application prefix: `/app`.
- Clients subscribe to `/user/queue/notifications` for per-user, in-app push.

### SLA breach routing

- `SlaBreachMonitorJob` now does two genuinely different things when an SLA instance changes breach status:
  1. Publishes a `SlaBreachEvent` (`triggerEntity = "SLA"`, `triggerType = "SLA_BREACH_RISK"`) so `AutomationRuleEngine` can evaluate any admin-configured rules against the event.
  2. Sends a direct `NotificationService.send` call when the instance transitions to `BREACHED`, providing a guaranteed baseline notification for the incident requester even when no rule exists.
- Reasoning: the `automation_rule` schema supports `SLA_BREACH_RISK` as a `trigger_type`, so it must be reachable. The direct call is the guaranteed, rule-independent baseline; the DomainEvent is the optional, admin-customizable path. These are not duplicate delivery mechanisms because they serve different jobs.

### Quartz digest job

- `NotificationDigestJob` is a Quartz `Job` that batches all `email_status = PENDING` notifications by user and sends one `SimpleMailMessage` digest per user.
- It filters by `NotificationPreference.digest_mode` so `HOURLY` and `DAILY` preferences receive batched emails instead of immediate messages.

## Tests

- `NotificationServiceTest`
  - `inAppSendPushesToCorrectUserSession` — proves the `SimpMessagingTemplate` receives the correct user destination for an in-app notification.
  - `emailSendRespectsDigestMode` — pending digest leaves `email_status = PENDING` and does not call `JavaMailSender`.
  - `immediateEmailIsSentWhenNoDigest` — `JavaMailSender` is used when `digest_mode = NONE`.
- `NotificationHandlerTest` — proves `SEND_NOTIFICATION` automation action routes to the user specified in `userId`.
- `NotificationDigestJobTest` — proves the Quartz job groups pending emails and sends one digest per user.

## Build

- Full Maven test suite: **93 tests run, 0 failures, 0 errors, 1 skipped** (`ItsPortalApplicationTests` skipped because Docker/Testcontainers is not available in this environment).
- `package.ps1` produced a clean `target/app.zip`.

## Deployment configuration

- **Azure App Service Application Settings required**:
  - `WebSockets = On` must be set to `1` (or `true`) in the Azure App Service configuration. Without this, the `/ws` STOMP/SockJS endpoint will not accept WebSocket connections.
  - `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, and `SPRING_MAIL_PASSWORD` should be configured for email delivery (or set to a no-op/stub sender in non-email environments).
