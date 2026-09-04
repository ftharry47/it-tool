# Redeploy Steps — Bug Fixes Applied

## Build Status
✅ **BUILD SUCCESS** — All 108 tests passed, 0 failures
✅ **app.zip ready** at: `target/app.zip`

## Changes Applied
1. **BUG 4 FIXED** — Added impact/urgency fields to incident form + error handling
2. **BUG 5 FIXED** — Removed class-level auth block on IncidentController, allow END_USER to read priorities/categories
3. **BUG 1 LIKELY FIXED** — Proper error/success toast handling prevents unmounted component updates

## Deployment via Kudu (Same as Before)

### Step 1: Upload app.zip
1. Go to Kudu: `https://itsm-alignedcardio-fkfkc0fuergxhrat.scm.azurewebsites.net`
2. Click **Debug Console** → **PowerShell**
3. Navigate: `cd D:\home\site\wwwroot`
4. Delete old JAR: `Remove-Item app.jar -Force -ErrorAction SilentlyContinue`
5. Upload `app.zip` via File Manager (click Upload button)
6. Extract: `Expand-Archive app.zip -DestinationPath . -Force`
7. Verify: `ls -Name app.jar` (should show app.jar)

### Step 2: Restart App
1. Go back to Azure Portal
2. Click **Restart** button on App Service
3. Wait 30-60 seconds for startup

### Step 3: Verify Startup
1. In Kudu, check logs:
   ```powershell
   cat D:\home\LogFiles\Application\spring.log | tail -50
   ```
2. Look for: `Started Application`
3. No errors should appear

### Step 4: Test the Fixes

**BUG 4 Test (Incident Form):**
- Navigate to /home/incidents or /dashboard/incidents
- Click "New Incident"
- Verify form has: Title, Description, Impact slider, Urgency slider, Priority dropdown, Category dropdown, Location, Phone, Attachment
- Fill form and submit
- Should see success toast (green notification top-right)
- Incident should appear in list

**BUG 5 Test (END_USER Dropdowns):**
- Log in as END_USER (not SUPER_ADMIN)
- Navigate to /home/incidents
- Click "New Incident"
- Open browser Network tab (F12)
- Verify these requests return 200 (not 403):
  - GET /api/incidents/priorities
  - GET /api/incidents/categories
- Dropdowns should be populated

**BUG 2 & 3 Tests (Project/Issue Detail):**
- Navigate to /dashboard/projects
- Click a project
- Check if project detail loads (no 500 error)
- Click an issue in backlog/board
- Check if issue detail loads (no 500 error)
- If either returns 500, check backend logs and report the error

## If Deployment Fails

### App Won't Start
1. Check logs: `cat D:\home\LogFiles\Application\spring.log`
2. Look for exception
3. Common issues:
   - Missing environment variables (check Azure App Service settings)
   - Database connection failed (check SPRING_DATASOURCE_URL)
   - Port binding issue (should be 8080)

### Rollback
If something breaks, you can quickly rollback:
1. Keep the previous app.jar backed up
2. In Kudu, replace new JAR with old one
3. Restart app

## Next Steps

1. Deploy the app.zip
2. Run the comprehensive testing (see COMPREHENSIVE_TEST_PLAN.md)
3. Report any failures
4. I'll fix and redeploy

---

**File Location:** `c:\Users\SriHariThangavel\Documents\Dev-IT\v4\target\app.zip`
