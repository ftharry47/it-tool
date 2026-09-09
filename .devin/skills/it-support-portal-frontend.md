---
description: Build and run the IT Support Portal frontend
---

# IT Support Portal Frontend

## Project layout

- `client/` — Vite + React + TypeScript SPA (`client/src/App.tsx` is the main enterprise dashboard).
- `public/app/` — CDN-based React app (`public/app/pages/HomePage.js`, `FormPage.js`, `DashboardPage.js`) served as static pages.
- `app.js` — Express server entry point.
- `build-client.js` — Direct Vite build wrapper.

## Build and run

1. Install dependencies from the project root:

   ```bash
   npm install
   ```

2. Build the Vite React client:

   ```bash
   npm run build:client
   ```

   This runs `build-client.js`, which invokes Vite directly from `client/node_modules/vite/bin/vite.js` and outputs to `client/dist/`.

3. Start the Express server:

   ```bash
   npm start
   ```

   `app.js` uses `process.env.PORT` (default `3000`) and falls back through `55000`, `55001`, `56000`, `57000`, `58000`, `59000` if the port is busy.

4. Run smoke tests (server must be running):

   ```bash
   npm test
   ```

## Development

- Use `cd client && npm run dev` for Vite hot-reload dev server.
- TypeScript must pass (`npx tsc --noEmit` from `client/`) before committing client changes.

## Homepage / form wiring

- The static `public/app/pages/HomePage.js` provides the public landing: search hero, quick action links (`/form`, `/dashboard`), live stats, feature cards, inline ticket tracking, and an SLA/support banner.
- `public/app/pages/FormPage.js` is the submit/track form with `tab` state (`submit`/`track`) and issue type selection.
- `client/src/App.tsx` renders the authenticated enterprise dashboard with `EnterpriseSidebar`, `JiraKanbanBoard`, `ServiceNowCatalog`, `ServiceNowCMDB`, analytics, knowledge base, and telemetry views.
- In `client/src/App.tsx`, service catalog tiles call `onOpenCatalogRequest(category)`, which sets `selectedCatalogItem` and opens `CreateTicketModal` with `initialTitle` pre-filled.

## Styling and icons

- `client/` uses Tailwind CSS and `lucide-react` for icons.
- `public/app/` uses Tailwind utility classes via CDN and inline SVG icons.
