# IT Support Portal

A modern, self-service IT support ticketing and administration system. The portal replaces the legacy Google Apps Script solution with a Node.js/Express backend and a React + TailwindCSS frontend.

## Features

- **Self-service ticket submission** with image attachments and issue-type/impact-area capture
- **Admin dashboard** with live metrics, team workload, SLA tracking, and activity feeds
- **SLA timer and escalation engine** with automatic breach checks and notifications
- **Auto-assignment** of tickets to available IT staff based on workload and support level
- **CMDB / asset inventory** with search, CRUD, and ticket linking
- **Advanced analytics** with ticket and SLA trends
- **Knowledge base** and **IT help-bot** self-service pages
- **Real-time activity feed** via Server-Sent Events
- **Role-based permissions** management UI
- **CSAT satisfaction** surveys, audit logs, scheduled maintenance banner
- **PWA support** with service worker caching, offline fallback, and app shortcuts
- **Bulk import/export** of tickets and users via CSV

## Technology Stack

- **Backend**: Node.js, Express, JSON file-based DB (`src/db.js`)
- **Frontend**: React, TypeScript, Vite, TailwindCSS, Framer Motion, Lucide icons
- **Email/SMS**: Nodemailer (SMTP or Microsoft Graph), optional Twilio
- **Build tool**: Vite (invoked through `build-client.js` or `npm`)

## Project Structure

```
.
├── app.js                  # Express server entry point
├── build-client.js         # Direct Vite build wrapper (used when npm wrapper is broken)
├── package.json            # Root package scripts and Express dependencies
├── client/                 # React SPA
│   ├── package.json        # Vite + React dependencies
│   ├── src/App.tsx         # Main application
│   └── dist/               # Production build output
├── public/                 # Static pages (admin, analytics, CMDB, help-bot, etc.)
├── src/                    # Backend modules
│   ├── handlers/           # API handler functions
│   ├── config.js           # App constants and mappings
│   ├── db.js               # JSON database helpers
│   ├── utils.js            # Utilities
│   ├── sla.js              # SLA engine
│   ├── email.js            # Email providers
│   ├── sms.js              # SMS provider
│   ├── audit.js            # Audit logging
│   ├── events.js           # Event emitter for SSE
│   ├── workflow.js         # Workflow and permissions
│   └── ...
└── tools/node/             # Bundled Node/npm toolchain
```

## Quick Start

### 1. Verify npm

The project includes a bundled Node/npm toolchain under `tools/node`. The root `npm` wrapper delegates to that toolchain.

```bash
npm --version
```

### 2. Install / restore dependencies

```bash
npm install
```

### 3. Build the React client

```bash
npm run build
# or, if you only want to build without starting the server:
npm run build:client
```

The build script invokes `build-client.js`, which runs Vite directly from `client/node_modules/vite/bin/vite.js`.

### 4. Start the server

```bash
npm start
```

The server will try to listen on the port defined by `PORT` (default `3000`) and fall back through `55000`, `55001`, `56000`, `57000`, `58000`, `59000` if the port is in use.

### 5. Open the portal

- Public home / form: `http://localhost:3000/`
- Admin dashboard: `http://localhost:3000/admin`
- Analytics: `http://localhost:3000/analytics`
- CMDB: `http://localhost:3000/cmdb`
- Live activity: `http://localhost:3000/live`
- Role permissions: `http://localhost:3000/roles`
- Knowledge base: `http://localhost:3000/kb`
- Help bot: `http://localhost:3000/help-bot`

### 6. Run the smoke tests

With the server already running:

```bash
npm test
```

This checks the home page, all new admin pages, the status endpoint, auto-assignment, and ticket submission.

## Configuration

Copy `.env.example` to `.env` and adjust values.

```bash
cp .env.example .env
```

Key environment variables:

| Variable | Description |
| --- | --- |
| `PORT` | Server port (default `3000`) |
| `DRY_RUN` | Skip real email/SMS sends when `true` |
| `FORM_ENABLED` | Enable the ticket form route |
| `DASHBOARD_ENABLED` | Enable the dashboard route |
| `EMAIL_MODE` | `smtp` or `graph` |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USER` / `SMTP_PASS` | SMTP fallback settings |
| `GRAPH_CLIENT_ID` / `GRAPH_CLIENT_SECRET` / `GRAPH_TENANT_ID` / `GRAPH_SENDER_USER` | Microsoft Graph settings |
| `SMS_ENABLED` / `SMS_CRITICAL_ONLY` / `TWILIO_SID` / `TWILIO_TOKEN` / `TWILIO_PHONE_NUMBER` | Twilio SMS settings |
| `MAINTENANCE_MODE` / `MAINTENANCE_TITLE` / `MAINTENANCE_MESSAGE` | Scheduled maintenance banner |
| `UPLOAD_DIR` | Directory for uploaded images |

## API

Most functions are exposed through a single generic endpoint:

```
POST /api/:fn
```

Request body:

```json
{
  "args": ["arg1", "arg2"],
  "user": { "email": "...", "role": "..." }
}
```

Selected endpoints:

- `POST /api/submitTicket` — create a new ticket
- `POST /api/validateUser` — authenticate a user
- `POST /api/getDashboardData` — dashboard stats
- `POST /api/toggleAutoAssign` — enable/disable auto-assignment
- `POST /api/setSetting` / `getAllSettings` — settings
- `POST /api/getAllRolePermissions` / `setRolePermissions` — role permissions
- `GET /api/events` — Server-Sent Events activity stream
- `GET /status` — system diagnostics

## Test Credentials

| Role | Email | Password |
| --- | --- | --- |
| Admin | `admin@work.local` | `admin123` |
| IT Agent | `agent@work.local` | `agent123` |
| User | `user@work.local` | `user123` |

## Notable Modules

- `src/handlers/tickets.js` — ticket CRUD, auto-assignment, and lifecycle
- `src/handlers/workflow.js` — workflow transitions and role permission handlers
- `src/handlers/cmdb.js` — asset inventory CRUD and ticket linking
- `src/analytics.js` — ticket and SLA analytics aggregation
- `src/sla.js` — SLA breach detection and escalation
- `src/audit.js` — audit log storage
- `src/events.js` — event bus for real-time feeds

## Notes

- Data is stored in a JSON file managed by `src/db.js`.
- Uploaded images are saved to `public/uploads/` (or `UPLOAD_DIR`) and served at `/uploads/`.
- The bundled `tools/node` directory provides a portable Node/npm toolchain; the root `npm` wrapper redirects to it.

## Deploy to Azure App Service

**Quick deploy script**
A ready-to-run PowerShell script is at `tools/deploy-azure.ps1`. From the project root run:

```powershell
.\tools\deploy-azure.ps1
```

It checks for `az` CLI, logs you in if needed, creates the App Service, builds a zip, and deploys it.

1. **Prepare the repo**
   - Make sure `package.json` has a `start` script (it does: `node app.js`).
   - Make sure `app.js` uses `process.env.PORT || 3000` (it does).
   - Create a `.gitignore` that excludes:
     - `node_modules/`
     - `tools/node/`
     - `app.log`, `*.log`
     - `public/uploads/*` (except `.gitkeep`)
     - `.env`, `.env.local`
     - local binaries: `node.exe`, `npm`, `npx`, `*.cmd`, `nodevars.bat`, `corepack*`
     - `.azure/`, `.vscode/`, `.idea/`

2. **Create the App Service (Azure CLI)**

```bash
az group create --name it-support-rg --location eastus
az appservice plan create --name it-support-plan --resource-group it-support-rg --sku B1 --is-linux
az webapp create --resource-group it-support-rg --plan it-support-plan --name it-support-portal --runtime "NODE|18-lts"
az webapp config appsettings set --resource-group it-support-rg --name it-support-portal --settings SCM_DO_BUILD_DURING_DEPLOYMENT=true
```

3. **Deploy from a zip**

```bash
# install dependencies and build package locally
npm install
npm run build
# zip the project without dev/local files (PowerShell)
Compress-Archive -Path app.js,package.json,package-lock.json,public,src -DestinationPath deploy.zip -Force
az webapp deploy --resource-group it-support-rg --name it-support-portal --src-path deploy.zip
```

4. **Optional: GitHub Actions**
   - In Azure Portal, get publish profile for the Web App.
   - Add `AZUREAPPSERVICE_PUBLISHPROFILE` as a GitHub secret.
   - Use the "Node.js to Azure Web App" GitHub Actions starter workflow.

5. **Notes**
   - Azure sets `PORT` automatically; `app.js` already reads `process.env.PORT`.
   - `public/uploads` uses local disk. For scaling beyond one instance, move uploads to Azure Blob Storage.
   - Set any secrets (email credentials, etc.) under *Configuration > Application settings* in the Azure Portal, not in code.