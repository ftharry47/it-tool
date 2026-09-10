# Kudu Redeploy Steps — Steps 1, 2, 4 Complete

## Build Status
✅ **BUILD SUCCESS** — All 108 tests passed, 0 failures
✅ **app.zip ready** at: `c:\Users\SriHariThangavel\Documents\Dev-IT\v4\target\app.zip`

---

## What's Deployed

### Step 1: Issue 1 Fixed ✅
- `/api/incidents/my` endpoint for END_USER to see only their own incidents
- Role-based filtering (END_USER → `/api/incidents/my`, AGENT+ → `/api/incidents`)

### Step 2: Issue 2 Fixed ✅
- IncidentDetail.tsx component with full incident management
- Status transitions (validated against IncidentStatusMachine)
- Assign action (AGENT+ only)
- Comments (public/internal visibility rules)
- Linked incidents
- Attachments
- Routes: `/home/incidents/:id` (END_USER) and `/dashboard/incidents/:id` (AGENT+)
- Click handler on incident list rows

### Step 4: Issue 4 Events Published ✅
- IncidentAssignedEvent (published when incident assigned)
- IncidentCommentedEvent (published when public comment added)
- IncidentStatusChangedEvent (already existed, confirmed working)
- All events ready for automation rules

---

## Deployment Steps (Kudu ZipDeploy)

### Step 1: Open Kudu Console
```
https://itsm-alignedcardio-fkfkc0fuergxhrat.scm.azurewebsites.net
```
Click **Debug Console** → **PowerShell**

### Step 2: Navigate to wwwroot
```powershell
cd D:\home\site\wwwroot
```

### Step 3: Clean Old JAR
```powershell
Remove-Item app.jar -Force -ErrorAction SilentlyContinue
```

### Step 4: Upload app.zip
- Click **Upload** button in File Manager
- Select: `c:\Users\SriHariThangavel\Documents\Dev-IT\v4\target\app.zip`
- Wait for upload to complete

### Step 5: Extract ZIP
```powershell
Expand-Archive app.zip -DestinationPath . -Force
```

### Step 6: Verify JAR
```powershell
ls -Name app.jar
```
Should output: `app.jar`

### Step 7: Restart App
- Go to Azure Portal
- Select App Service: `itsm-alignedcardio-fkfkc0fuergxhrat`
- Click **Restart** button
- Wait 30-60 seconds for startup

### Step 8: Verify Startup
```powershell
cat D:\home\LogFiles\Application\spring.log | tail -50
```
Look for: `Started Application` (no errors)

---

## Post-Deployment Testing

### Test 1: END_USER Can See Only Their Incidents
1. Log in as END_USER
2. Navigate to `/home/incidents`
3. **Verify:** Only incidents where END_USER is the reporter are shown
4. Click on an incident
5. **Verify:** Navigates to `/home/incidents/{id}`
6. **Verify:** Can see details, status, public comments, attachments
7. **Verify:** Cannot see "Status Transition", "Assign", "Link Incidents" sections

### Test 2: AGENT Can See All Incidents and Edit
1. Log in as AGENT
2. Navigate to `/dashboard/incidents`
3. **Verify:** All incidents in org are shown
4. Click on an incident
5. **Verify:** Navigates to `/dashboard/incidents/{id}`
6. **Verify:** Can see "Status Transition" dropdown
7. **Verify:** Can see "Assign" dropdown
8. **Verify:** Can see "Link Incidents" section
9. Change status
10. **Verify:** Status changes successfully
11. Assign to a user
12. **Verify:** Incident assigned successfully
13. Post a public comment
14. **Verify:** Comment appears in list
15. Post an internal comment
16. **Verify:** Internal comment appears (AGENT can see, END_USER cannot)

### Test 3: Events Are Published
1. As AGENT, change incident status
2. Check application logs for: `IncidentStatusChangedEvent published`
3. As AGENT, assign incident
4. Check application logs for: `IncidentAssignedEvent published`
5. As AGENT, post public comment
6. Check application logs for: `IncidentCommentedEvent published`

### Test 4: Automation Rules (Manual Setup Required)
1. Go to Admin → Automation
2. Create automation rule:
   - Trigger: `INCIDENT.STATUS_CHANGED`
   - Action: `SEND_NOTIFICATION`
   - Recipients: `incident.requester`
3. Change incident status
4. **Verify:** Notification appears in notification list for reporter
5. Repeat for `INCIDENT.ASSIGNED` and `INCIDENT.COMMENTED` triggers

---

## Troubleshooting

### App Won't Start
1. Check logs: `cat D:\home\LogFiles\Application\spring.log | tail -100`
2. Look for exception stack trace
3. Common issues:
   - Missing environment variables
   - Database connection failed
   - Port binding issue

### Incident Detail Page Not Loading
1. Clear browser cache (Ctrl+Shift+Delete)
2. Hard refresh (Ctrl+F5)
3. Check browser console (F12) for JavaScript errors
4. Verify route is correct: `/dashboard/incidents/{id}` or `/home/incidents/{id}`

### Events Not Publishing
1. Check application logs for event publishing messages
2. Verify eventPublisher is injected in service
3. Verify @Transactional is on the method
4. Check if automation rules are created

### Notifications Not Appearing
1. Verify automation rules are created in Automation Admin
2. Verify rule trigger matches event type (e.g., `INCIDENT.STATUS_CHANGED`)
3. Verify rule action is `SEND_NOTIFICATION`
4. Check notification table in database for records
5. Verify END_USER is logged in to see notifications

### Rollback
If something breaks:
1. Keep previous app.jar backed up
2. Replace new JAR with old one in Kudu
3. Restart app

---

## Files Modified/Created

### Modified
- `IncidentService.java` — Added IncidentAssignedEvent publishing
- `IncidentCommentService.java` — Added IncidentCommentedEvent publishing
- `routes/index.tsx` — Added incident detail routes
- `Incidents.tsx` — Added click handler, role-based endpoint selection

### Created
- `IncidentDetail.tsx` — Full incident detail component
- `IncidentAssignedEvent.java` — New event
- `IncidentCommentedEvent.java` — New event

---

## Verification Checklist

- [ ] App starts without errors
- [ ] END_USER sees only their own incidents in `/home/incidents`
- [ ] END_USER can click incident and view detail (read-only)
- [ ] AGENT sees all incidents in `/dashboard/incidents`
- [ ] AGENT can click incident and view detail (full control)
- [ ] AGENT can change incident status
- [ ] AGENT can assign incident
- [ ] AGENT can post public and internal comments
- [ ] END_USER can post public comments (not internal)
- [ ] Status transitions match IncidentStatusMachine exactly
- [ ] Events are published to logs
- [ ] Automation rules are created
- [ ] Notifications appear for END_USER when status changes
- [ ] Notifications appear for END_USER when incident assigned
- [ ] Notifications appear for END_USER when public comment added

---

## Next Steps

1. Deploy app.zip to Kudu (follow steps above)
2. Run post-deployment tests
3. Create automation rules in Automation Admin UI
4. Test notifications end-to-end
5. Report any issues

---

**Ready to deploy?**

**File Location:** `c:\Users\SriHariThangavel\Documents\Dev-IT\v4\target\app.zip`
