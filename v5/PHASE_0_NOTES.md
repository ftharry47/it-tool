# Phase 0 — Scaffolding Notes

## What was built
1. **Maven Spring Boot 3.3.5 skeleton** at `v4/`
   - `pom.xml` with Java 21, all spec dependencies, frontend-maven-plugin
   - Spring Boot repackage produces `target/app.jar`
2. **Packaging for Kudu ZipDeploy**
   - `package.ps1` and `package.sh` run `mvn clean package` and zip `app.jar` + `startup.sh`
   - `startup.sh` runs `java -jar /home/site/wwwroot/app.jar`
3. **Flyway base migration (`V1__base.sql`)**
   - `pgcrypto` extension
   - `org` table with default single org
   - `set_updated_at()` trigger function + trigger on `org`
4. **JPA `BaseEntity`**
   - `id` (UUID), `org_id`, `created_at`, `updated_at`, `created_by`, `updated_by`, `deleted_at`
   - Soft delete via `@Where(clause = "deleted_at IS NULL")`
   - Timestamps are DB-managed via `default now()` + `set_updated_at` trigger
5. **React 18 + Vite scaffold in `src/main/frontend/`**
   - Tailwind CSS, shadcn-style CSS variables, dark mode
   - Placeholder `App.tsx` with `react-router-dom` routes
   - `vite.config.ts` builds into `src/main/resources/static`
6. **Testcontainers integration test**
   - `src/test/resources/application-test.properties` uses `jdbc:tc:postgresql:16-alpine:///itsm`
   - `ItsPortalApplicationTests.java` loads the context with the base migration
7. **Design system document** at `DESIGN_SYSTEM.md`

## Assumptions made
- Package root: `com.alignedcardio.itsm`
- `created_by` and `updated_by` are stored as UUIDs now; FK constraints to `app_user(id)` will be added in Phase 1.
- Single default org with a fixed UUID (`00000000-0000-0000-0000-000000000001`).
- Manual Kudu drag-and-drop is the only deploy path; no CI pipeline.

## How to verify
1. Install **Java 21 JDK** and **Maven 3.9+** (or `./mvnw` if wrapper is added later).
2. From `v4/` run:
   ```powershell
   .\package.ps1
   ```
   or on Linux/mac:
   ```bash
   ./package.sh
   ```
3. Confirm `target/app.zip` contains:
   - `app.jar`
   - `startup.sh`
4. Drag `target/app.zip` into Kudu ZipDeploy and set the App Service `Startup Command` to:
   ```
   bash /home/site/wwwroot/startup.sh
   ```
5. Set Application Settings (per Section 2B) and restart.

## Current blockers
- The build could not be run in this environment because **Java and Maven are not installed**.
- Running the frontend requires `npm install` (handled by `frontend-maven-plugin` during `mvn package`).
