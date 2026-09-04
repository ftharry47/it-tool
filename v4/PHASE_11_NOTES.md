# Phase 11 — Hardening + Final Packaging

## 11.1 — Security audit pass

### Status

Completed, tested, and packaged.

- Full test suite: **105 tests, 0 failures, 0 errors, 1 skipped**.
- `target/app.zip` rebuilt cleanly.
- 1a: `IncidentController` missing `@PreAuthorize` — fixed with class-level AGENT+ access.
- 1b: No user-driven raw SQL found; `IssueService` native query for project sequences and `TsVectorKnowledgeBaseSearch` are parameter-safe.
- 1c: `BaseEntity` `@Where(clause = "deleted_at IS NULL")` applies to all 38 entity classes.
- 1d: `is_active`, `mfa_enabled`, and `manager_id` added to `app_user`, `UserService.syncFromJwt` rejects deactivated users at every authenticated request, and admin `PATCH /api/admin/users/{id}` is available.

### Known gap from 11.1.5 confirmation

- `IncidentController` had no `@PreAuthorize` since Phase 2; any authenticated user (including `END_USER`) could call `list`, `create`, `get`, `update`, `PATCH /{id}/assign`, `PATCH /{id}/status`, `GET /priorities`, and `GET /categories`. `IncidentService` has no service-layer role checks, so `SecurityConfig`'s generic `/api/**` authentication was the only gate.
- `IncidentCommentService` internal/public comment filtering was never affected by this gap and remains in place.
- `ProblemController`, `ChangeController`, `ServiceRequestController`, and `IssueController` were all confirmed to have appropriate endpoint-level authorization.

## 11.2 — Load test

### Status

**Deferred** — cannot be executed in this environment.

### Why it was deferred

- No PostgreSQL instance is running on `127.0.0.1:5432` (verified by TCP probe).
- `psql` / `pg_isready` are not installed.
- `docker` is not installed, so a local database cannot be started in-place.

### What was still produced

- `scripts/load_test.ps1` — a PowerShell load-test harness for a live environment.

### Manual run instructions (to be executed once deployed or on a dev box with Postgres)

1. Start the app with SQL logging and Hibernate statistics enabled:
   ```bash
   SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:<port>/<db> \
   SPRING_DATASOURCE_USERNAME=<user> \
   SPRING_DATASOURCE_PASSWORD=<pass> \
   java -jar target/app.jar \
     --spring.jpa.show-sql=true \
     --spring.jpa.properties.hibernate.generate_statistics=true
   ```
2. Obtain an AGENT+ JWT.
3. Run the load test:
   ```powershell
   .\scripts\load_test.ps1 -BaseUrl "http://localhost:8080" -Token "<AGENT_JWT>" -Total 100 -Warmup 10
   ```
4. Capture p95/p99 and error counts from the console output.
5. Inspect the application log for repeated single-row `SELECT` statements following a list query (N+1 signature).
6. Run the index audit query below in Postgres and verify FK/filter columns are covered.

### Index audit query

```sql
SELECT
    schemaname,
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE tablename IN ('incident', 'issue', 'problem')
ORDER BY tablename, indexname;
```

Cross-check the resulting index list against:

- **Foreign keys** on these tables (`org_id`, `requester_id`, `assignee_id`, `assignee_team_id`, `category_id`, `priority_id`, etc.)
- **List/filter columns** used by the list endpoints (`status`, `priority`, `category`, `created_at`, `updated_at`, `assignee_id`)
- **Section 3 global convention:** every FK has an index and every filter column used in list views has an index.

### Notes for when this is run

- The primary finding to watch for is repeated `SELECT incident0_.id ...` or similar single-row queries emitted after the list query — that indicates the list endpoint is triggering an N+1 on lazy associations.
- `pg_stat_statements` (if enabled) will also surface the most frequent queries and whether they are doing sequential scans.

## 11.3 — Accessibility audit

### Status

Completed.

- Frontend build (`npm run build`) passed and produced `../resources/static`.
- Full Java build + `app.zip` rebuild passed after the frontend changes.

### What was checked

- All 5 TSX pages: `App.tsx`, `Dashboard.tsx`, `Incidents.tsx`, `Login.tsx`, `main.tsx`
- `index.html` for `lang` attribute and `title`
- Keyboard navigation paths (links, buttons, form inputs)
- ARIA / semantic HTML
- Visible focus states

### Findings flagged and fixed

| File | Issue | Fix |
|------|-------|-----|
| `Incidents.tsx` | Form labels were not programmatically associated with inputs (no `htmlFor`/`id`) | Added `htmlFor` to all `<label>`s and matching `id` to all `<input>`, `<textarea>`, and `<select>` fields |
| `Incidents.tsx` | Data table headers had no `scope` and the table had no caption | Added `scope="col"` to all `<th>` elements and `<caption className="sr-only">` |
| `Incidents.tsx` | Loading message was not announced to screen readers | Added `role="status"` and `aria-live="polite"` |
| `Incidents.tsx` | Buttons relied on default browser focus outline | Added `focus-visible:ring-2` focus rings to all buttons |
| `Dashboard.tsx` | `Link` and `button` had no explicit focus state | Added `focus-visible:ring-2` focus rings |
| `Login.tsx` | Sign-in button had no explicit focus state | Added `focus-visible:ring-2` focus ring |

### Static color-contrast check

- `scripts/contrast_check.mjs` reads the HSL tokens from `src/main/frontend/src/index.css` and computes WCAG 2.1 AA ratios.
- Result after token adjustments: **0 failures, 24 passes**.
- Token changes applied:
  - `destructive`: `0 84% 60%` → `0 75% 44%` (meets 4.5:1 with white text)
  - `muted-foreground` (light): `240 4% 46%` → `240 4% 44%` (meets 4.5:1 on muted/card)
  - `border` / `input` (light): `240 6% 90%` → `240 5% 30%` (meets 3:1 on white)
  - `border` / `input` (dark): `0 0% 14%` → `0 0% 38%` (meets 3:1 on black)
- `DESIGN_SYSTEM.md` updated to the same hex values.

### Manual keyboard / screen-reader checklist (post-deploy)

- [ ] Press `Tab` from the login page through `Sign in with Microsoft`, dashboard `View Incidents`/`Sign out`, then `Back`/`New Incident`.
- [ ] Inside the incident form, `Tab` through `Title`, `Description`, `Priority`, `Category`, and `Submit` in order; verify each field receives a visible `focus-visible` ring.
- [ ] Open the incident form and press `Esc` — does focus return to the `New Incident` button? (If not, add that behavior.)
- [ ] With a screen reader active, submit a new incident and confirm the `aria-live="polite"` loading message and the resulting list update are announced.
- [ ] With a screen reader active, change the incident `Priority`/`Category` `<select>` and confirm the chosen value is announced.
- [ ] Run the table in `Incidents.tsx` through a screen reader; confirm header cells (`<th scope="col">`) and the hidden `<caption>` are read.

## 11.4 — Playwright E2E suite

### Test-only auth profile

- Profile name: `e2e-fixed-auth` (deliberately unambiguous, not "test").
- Gating: the entire `E2eFixedAuthConfig` class is annotated with `@Profile("e2e-fixed-auth")`, so the `JwtDecoder` and `CommandLineRunner` beans are **only constructed when that exact profile is active**. There is no runtime `if` flag inside always-present code.
- Activation: `java -jar target/app.jar --spring.profiles.active=e2e-fixed-auth` or `SPRING_PROFILES_ACTIVE=e2e-fixed-auth`.
- Fixed tokens: `test-end-user` (END_USER role) and `test-agent` (AGENT role) are accepted; all other tokens are rejected.
- Test users are auto-seeded on startup via `E2eFixedAuthConfig.e2eUserSeeder`.

### What is in the package

- `src/main/java/com/alignedcardio/itsm/config/E2eFixedAuthConfig.java`
- `src/main/resources/application-e2e-fixed-auth.properties`
- `e2e/package.json`
- `e2e/playwright.config.ts`
- `e2e/tests/itsm.spec.ts` with the five requested scenarios

### What cannot run in this environment

- The Playwright suite requires a running backend with Postgres and the `e2e-fixed-auth` profile. No local Postgres is available (same blocker as 11.2), so the suite is **written but not executed here**.
- `npm install` succeeded in `e2e/`; `npx playwright test` can be run in a real environment.

### Manual Microsoft login checklist (post-deploy)

- [ ] Open the deployed app in a browser.
- [ ] Click `Sign in with Microsoft` and complete the real Entra ID login.
- [ ] Confirm the redirect returns to the `/dashboard` route.
- [ ] Confirm `/api/auth/me` returns the correct user with `isActive`, `mfaEnabled`, and `managerId`.
- [ ] Confirm deactivating a user in the admin `PATCH` endpoint invalidates their next request.

### Airtight gating confirmation

- `grep` of the full `src/` tree shows the **only** `JwtDecoder` construction is inside `E2eFixedAuthConfig.e2eFixedJwtDecoder()`.
- No other class imports `JwtDecoder` or creates a fixed-token implementation.
- No call site instantiates `E2eFixedAuthConfig` directly; it is only a Spring `@Configuration` class whose beans are created when `e2e-fixed-auth` is active.
- The class itself is compiled into the JAR, but it is inert without the profile. This is a documented, accepted deployment risk: the beans cannot activate by any code path other than `SPRING_PROFILES_ACTIVE=e2e-fixed-auth`.

### Kudu / deployment risk

- [ ] Verify the `target/app.zip` package does not need the E2E auth profile; it is the same JAR, but the `e2e-fixed-auth` beans are disabled unless `SPRING_PROFILES_ACTIVE` contains `e2e-fixed-auth`.
- [ ] **Critical:** Ensure `SPRING_PROFILES_ACTIVE` in Azure Application Settings **never** includes `e2e-fixed-auth` in production.

### Project board / automation rule UI gap

- **Finding:** The backend has 21 REST controllers including `ProjectController`, `SprintController`, `WorkflowController`, and `AutomationRuleController`, but the frontend (`src/main/frontend/src/pages/`) contains only `Dashboard.tsx`, `Incidents.tsx`, and `Login.tsx`.
- **App.tsx** only routes to `/`, `/login`, `/incidents`, and `/dashboard`.
- **Conclusion:** This is a **feature gap**, not a test-writing gap. The UI for project boards, sprints, workflows, and automation rules was never built, even though the backend APIs exist. This should be flagged and scheduled; it is not resolvable in Phase 11.
- As a result, the two `test.fixme` Playwright scenarios for project board and automation rule are correctly deferred to the future UI implementation.

## 11.5 — Final packaging verification

### Build artifacts

- `mvn package` completed: `105 tests, 0 failures, 0 errors, 1 skipped`.
- `target/app.jar` produced.
- `target/app.zip` produced with `target/app.jar` and `startup.sh`.
- `package.ps1` was blocked by a locked `target/node/node_modules` directory during `mvn clean`; the packaging step was completed with `mvn package` followed by `Compress-Archive`.
- Embedded frontend static assets are in `src/main/resources/static` and packaged in the jar.

### Kudu deploy checklist

- [ ] Upload `target/app.zip` to Azure App Service Kudu and deploy.
- [ ] Confirm `JAVA_HOME` and `WEBSITE_LOCAL_CACHE_OPTION` are appropriate.
- [ ] Confirm `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` are set.
- [ ] Confirm `AZURE_AD_TENANT_ID` is set and `SPRING_PROFILES_ACTIVE` does **not** include `e2e-fixed-auth`.
- [ ] Confirm the app starts and Flyway migrations run.
- [ ] Smoke test the home page, login, and incident form.

## 11.6 — Application Insights verification

### Current state

- `pom.xml` now includes `com.microsoft.azure:applicationinsights-runtime-attach:3.4.19`.
- `ItsPortalApplication.main` calls `ApplicationInsights.attach()` (class `com.microsoft.applicationinsights.attach.ApplicationInsights`) before `SpringApplication.run(...)` to enable auto-instrumentation at startup. The correct class in this artifact is `ApplicationInsights`, not `RuntimeAttach` — the latter does not exist in the jar and was a typo that would not compile.
- The only required deploy-time setting is `APPLICATIONINSIGHTS_CONNECTION_STRING` in Azure Application Settings.

### Post-deploy checklist

- [ ] Set `APPLICATIONINSIGHTS_CONNECTION_STRING` in Azure Application Settings.
- [ ] Verify request, dependency, and exception telemetry flows to the Azure Application Insights instance.
- [ ] Confirm the cloud role name is `itsm-portal` from `spring.application.name`.

## 11.7 — KNOWN GAP: audience validation not yet active

### What it is

- The backend `SecurityConfig` now has a custom `JwtDecoder` that can validate the `aud` (audience) claim of incoming Bearer tokens.
- The validator reads `spring.security.oauth2.resourceserver.jwt.audience`, which maps to the `AZURE_AD_AUDIENCE` Azure Application Setting.
- **For the first internal pilot, `AZURE_AD_AUDIENCE` must be left unset.**

### Why it must be left off

- The frontend's MSAL configuration in `src/main/frontend/src/auth/authConfig.ts` currently requests `User.Read`.
- Tokens from a `User.Read` request have `aud` = Microsoft Graph, not this app's own API.
- If `AZURE_AD_AUDIENCE` is set before the frontend is changed, the backend will reject every valid token and sign-in will fail.

### Why this is a real gap, not cosmetic

- Without audience validation, the backend currently accepts **any valid Entra ID token from this tenant**, even if it was issued for a completely different application.
- This is the documented tenant-wide token cross-application concern: signature + issuer only proves the token came from the right tenant, not that it was intended for this API.

### What must happen before this is trusted with real sensitive data at scale

1. In the Entra App Registration, **Expose an API** and add a scope (e.g. `access_as_user`) under an App ID URI such as `api://<client-id>`.
2. Update `src/main/frontend/src/auth/authConfig.ts` so MSAL requests that API scope instead of `User.Read`.
3. Set `AZURE_AD_AUDIENCE` in Azure Application Settings to the App ID URI (`api://<client-id>`).
4. Rebuild `app.zip` with `\package.ps1` and redeploy.

### Status

- **Pending after Phase 11 pilot deploy.** Not urgent for an initial internal pilot, but must be scheduled before production trust.
