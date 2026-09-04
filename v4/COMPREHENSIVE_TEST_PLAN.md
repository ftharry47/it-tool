# Comprehensive Testing Plan — All 17 Nav Items

## Build Status
✅ **BUILD SUCCESS** — All 108 tests passed, 0 failures, app.zip ready

## Deployment Steps (Required Before Testing)
1. Upload `target/app.zip` to Kudu (see KUDU_REDEPLOY_STEPS.md)
2. Restart the app
3. Check backend logs for any startup errors

## Testing Checklist — Report Status for Each

### END_USER Role (/home/*)
**Prerequisites:** Logged in as END_USER (not SUPER_ADMIN)

- [ ] **1. Home** — Page loads without errors, shows welcome/hero section
  - **How to verify:** Navigate to /home, check browser console for errors
  - **Expected:** Page displays, no 500 errors

- [ ] **2. My Incidents (Submit Ticket)** — Form loads with all fields
  - **How to verify:** Navigate to /home/incidents, click "New Incident"
  - **Expected:** Form shows Title, Description, Impact (1-5 slider), Urgency (1-5 slider), Priority dropdown, Category dropdown, Location, Phone, Attachment
  - **Network check:** GET /api/incidents/priorities and GET /api/incidents/categories should return 200 (not 403)

- [ ] **3. My Incidents (Submit)** — Form submission works
  - **How to verify:** Fill form, click Submit
  - **Expected:** Success toast appears, incident appears in list, no silent failures
  - **Network check:** POST /api/incidents should return 201

- [ ] **4. Service Catalog** — Browse page loads
  - **How to verify:** Navigate to /home/catalog
  - **Expected:** Page loads, shows catalog items (or "no items" if empty)
  - **Network check:** GET /api/v1/catalog should return 200

- [ ] **5. Knowledge Base** — Browse page loads
  - **How to verify:** Navigate to /home/kb
  - **Expected:** Page loads, shows KB articles (or "no articles" if empty)
  - **Network check:** GET /api/v1/kb should return 200

### AGENT/TEAM_LEAD Role (/dashboard/*)
**Prerequisites:** Logged in as AGENT or TEAM_LEAD

- [ ] **6. Dashboard** — Loads with real data
  - **How to verify:** Navigate to /dashboard
  - **Expected:** Shows dashboard with metrics/widgets, not placeholders
  - **Network check:** Check network tab for data requests

- [ ] **7. Incidents** — List loads and create works
  - **How to verify:** Navigate to /dashboard/incidents
  - **Expected:** List shows incidents, "New Incident" form works (same as #3)
  - **Network check:** GET /api/incidents should return 200

- [ ] **8. Problems** — List and detail load
  - **How to verify:** Navigate to /dashboard/problems, click a problem
  - **Expected:** List loads, detail page loads with problem info
  - **Network check:** GET /api/v1/problems and GET /api/v1/problems/{id} return 200

- [ ] **9. Changes** — List and detail load
  - **How to verify:** Navigate to /dashboard/changes, click a change
  - **Expected:** List loads, detail page loads with change info
  - **Network check:** GET /api/v1/changes and GET /api/v1/changes/{id} return 200

- [ ] **10. Service Requests** — List and detail load
  - **How to verify:** Navigate to /dashboard/service-requests, click a request
  - **Expected:** List loads, detail page loads with request info
  - **Network check:** GET /api/v1/service-requests and GET /api/v1/service-requests/{id} return 200

- [ ] **11. KB Articles** — List and editor load
  - **How to verify:** Navigate to /dashboard/kb
  - **Expected:** List loads, clicking an article shows editor
  - **Network check:** GET /api/v1/kb returns 200

- [ ] **12. Projects** — List loads, board loads, detail loads
  - **How to verify:** Navigate to /dashboard/projects, click a project
  - **Expected:** List loads, project detail page loads with board/backlog/sprints tabs
  - **Network check:** GET /api/v1/projects returns 200, GET /api/v1/projects/{id} returns 200 (BUG 2 check)

- [ ] **13. Issues** — Detail page loads
  - **How to verify:** From Projects detail, click an issue in backlog or board
  - **Expected:** Issue detail page loads with comments, links, status
  - **Network check:** GET /api/v1/issues/{id} returns 200 (BUG 3 check)

- [ ] **14. Reports** — Dashboard loads with charts
  - **How to verify:** Navigate to /dashboard/reports
  - **Expected:** Reports dashboard loads with real charts (or empty if no data)
  - **Network check:** GET /api/v1/reports returns 200

- [ ] **15. Notifications** — Preferences page loads and saves
  - **How to verify:** Navigate to /dashboard/notifications
  - **Expected:** Preferences page loads, can toggle settings and save
  - **Network check:** GET /api/v1/notifications/preferences and PATCH return 200

### ADMIN/SUPER_ADMIN Role (/admin/*, plus all above)
**Prerequisites:** Logged in as SUPER_ADMIN

- [ ] **16. Users** — List loads, role assignment works
  - **How to verify:** Navigate to /admin/users
  - **Expected:** User list loads, can assign roles
  - **Network check:** GET /api/v1/users and PATCH /api/v1/users/{id}/roles return 200

- [ ] **17. Catalog Admin** — List loads, create/edit works
  - **How to verify:** Navigate to /admin/catalog
  - **Expected:** Catalog list loads, can create/edit items with form_schema JSON
  - **Network check:** GET /api/v1/catalog and POST/PATCH return 200

- [ ] **18. Workflows** — List loads, builder works
  - **How to verify:** Navigate to /admin/workflows
  - **Expected:** Workflow list loads, can create/edit workflows
  - **Network check:** GET /api/v1/workflows and POST/PATCH return 200

- [ ] **19. Automation** — Rule list loads, create/edit works
  - **How to verify:** Navigate to /admin/automation
  - **Expected:** Rule list loads, can create rules with conditions and actions
  - **Network check:** GET /api/v1/automation/rules and POST/PATCH return 200

## Bug Verification

### BUG 1 — React #301 Error
**Status:** LIKELY FIXED
**How to verify:** 
- Submit an incident form (BUG 4 test)
- Check browser console for React errors
- Should see success toast, not crash

### BUG 2 — Project Detail 500
**Status:** NEEDS VERIFICATION
**How to verify:**
- Navigate to /dashboard/projects
- Click a project
- Check if GET /api/v1/projects/{id} returns 200 or 500
- If 500, check backend logs: `cat D:\home\LogFiles\Application\spring.log | grep -A 5 "projects"`

### BUG 3 — Issue Detail 500
**Status:** NEEDS VERIFICATION
**How to verify:**
- Navigate to /dashboard/projects/{id}/issues/{id}
- Check if GET /api/v1/issues/{id} returns 200 or 500
- If 500, check backend logs: `cat D:\home\LogFiles\Application\spring.log | grep -A 5 "issues"`

### BUG 4 — Incident Form Doesn't Submit
**Status:** FIXED ✅
**How to verify:**
- Navigate to /home/incidents or /dashboard/incidents
- Fill form with Title, Impact, Urgency, Category, Priority
- Click Submit
- Should see success toast and incident appears in list
- Check network tab: POST /api/incidents should return 201

### BUG 5 — Priority/Category Dropdowns Not Loading
**Status:** FIXED ✅
**How to verify:**
- Navigate to /home/incidents as END_USER
- Click "New Incident"
- Check network tab: GET /api/incidents/priorities and GET /api/incidents/categories should return 200 (not 403)
- Dropdowns should be populated

## Reporting Results

After testing, provide a table like:

| Item | Status | Notes |
|------|--------|-------|
| 1. Home | WORKS | Loads without errors |
| 2. My Incidents (Form) | WORKS | All fields present, dropdowns populated |
| 3. My Incidents (Submit) | WORKS | Success toast, incident created |
| ... | ... | ... |
| BUG 2 | BROKEN | GET /api/v1/projects/{id} returns 500: [error details] |
| BUG 3 | BROKEN | GET /api/v1/issues/{id} returns 500: [error details] |

## If BUG 2 or BUG 3 Fail

1. Check backend logs in Kudu:
   ```powershell
   cat D:\home\LogFiles\Application\spring.log | tail -100
   ```

2. Look for exception stack trace

3. Share the exact error with me, and I'll fix it

4. Rebuild and redeploy

---

**You must do this testing yourself** — I cannot interact with the running app. Once you complete the testing and report any failures, I'll fix them immediately.
