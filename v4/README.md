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

### Build & package

```powershell
# Frontend build is embedded automatically by package.ps1
mvn clean package -DskipTests
.\package.ps1
```

Output: `target/app.zip`

### Azure App Service
1. In Azure Portal, set the Java 21 runtime stack.
2. Upload `target/app.zip` to `https://<yourapp>.scm.azurewebsites.net/ZipDeploy`.
3. Set **Configuration → Application settings**, then restart the App Service.

### Required Application Settings

| Setting | Purpose | Example / Note |
|--------|---------|----------------|
| `APP_BASE_URL` | Public HTTPS URL used for email links and attachment URLs | `https://<yourapp>.azurewebsites.net` (no trailing slash) |
| `ATTACHMENT_STORE_PATH` | Persistent filesystem path for incident attachments | `/home/attachments` on Azure Linux App Service; defaults to `target/attachments` locally |
| `AZURE_GRAPH_ENABLED` | Send email via Microsoft Graph instead of SMTP | `true` |
| `AZURE_GRAPH_FROM_MAILBOX` | Sender mailbox for Graph emails | `itsm@yourdomain.com` |
| `AZURE_GRAPH_CLIENT_ID` / `AZURE_GRAPH_CLIENT_SECRET` / `AZURE_GRAPH_TENANT_ID` | Graph service principal credentials | Register an app with `Mail.Send` Application permission + admin consent |
| `AZURE_AD_TENANT_ID` / `AZURE_AD_AUDIENCE` | Validate incoming access tokens | Same Entra app registration |

### Frontend build environment

`src/main/frontend/.env.production` should contain:

```
VITE_AZURE_CLIENT_ID=<your-spa-app-id>
VITE_AZURE_TENANT_ID=<your-tenant-id>
VITE_APP_BASE_URL=https://<yourapp>.azurewebsites.net
```

If `VITE_APP_BASE_URL` is not set, the production build falls back to `window.location.origin` for MSAL redirect URIs.

## Project structure
- `pom.xml` — Maven build, frontend integration, Spring Boot deps
- `src/main/java/com/alignedcardio/itsm/` — Java backend
- `src/main/frontend/` — React SPA
- `src/main/resources/db/migration/` — Flyway migrations
- `package.ps1` / `package.sh` — Kudu zip packager
