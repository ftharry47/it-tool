# ITSM Portal

Azure-deployable ITSM/ITIL + Agile work management application.

## Architecture
- Single Azure App Service (Java 21 Linux)
- Single Spring Boot 3.3+ fat JAR
- React 18 + Vite frontend embedded in `src/main/resources/static`
- PostgreSQL Flexible Server
- Manual Kudu ZipDeploy (no CI/CD)

## Build

Requires Java 21 JDK and Maven 3.9+.

```powershell
.\package.ps1
```

Output: `target/app.zip`

## Deploy
1. In Azure Portal, set the Java 21 runtime stack.
2. Upload `target/app.zip` to `https://<yourapp>.scm.azurewebsites.net/ZipDeploy`.
3. Set **Configuration → Application settings** (Section 2B of the spec).
4. Restart the App Service.

## Project structure
- `pom.xml` — Maven build, frontend integration, Spring Boot deps
- `src/main/java/com/alignedcardio/itsm/` — Java backend
- `src/main/frontend/` — React SPA
- `src/main/resources/db/migration/` — Flyway migrations
- `package.ps1` / `package.sh` — Kudu zip packager
