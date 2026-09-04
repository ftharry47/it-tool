# Incident Assignment Guide

## Fixed Issues
✅ **END_USER can now submit tickets** — Changed POST /api/incidents to `isAuthenticated()`
✅ **Service Catalog 403 error** — Needs investigation (likely same authorization issue)

## How to Assign a Ticket

### Backend Endpoint
```
PATCH /api/v1/incidents/{incidentId}/assign
Authorization: hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')
Body: { "assigneeId": "<user-uuid>" }
```

### Frontend Steps (After Deployment)
1. **Log in as ADMIN/AGENT/TEAM_LEAD**
2. Navigate to **Incidents** (Dashboard)
3. **Click on an incident** in the list (e.g., incident #1000)
4. In the detail view, look for **Assignee** field
5. Click **Assign** button or select from dropdown
6. Choose an agent/team lead/admin user
7. Click **Confirm** or **Save**

### Current Status
- ✅ Backend endpoint exists and is working
- ⏳ Frontend UI for assignment may need to be added/verified

## What's Needed
To fully enable assignment in the UI, the incident detail view needs:
1. A list of available assignees (agents/team leads)
2. An "Assign" button or dropdown
3. A confirmation dialog
4. Call to `PATCH /api/v1/incidents/{id}/assign` with assigneeId

## Next Steps
1. Deploy the updated app.zip
2. Test END_USER ticket submission (should now work)
3. Test assignment from ADMIN/AGENT view
4. If assignment UI is missing, I'll add it

---

**File Location:** `c:\Users\SriHariThangavel\Documents\Dev-IT\v4\target\app.zip`
