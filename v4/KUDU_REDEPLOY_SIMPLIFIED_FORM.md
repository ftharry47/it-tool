# Kudu Redeploy — Simplified Incident Form

## Build Status
✅ **BUILD SUCCESS** — All 108 tests passed, 0 failures
✅ **app.zip ready** at: `c:\Users\SriHariThangavel\Documents\Dev-IT\v4\target\app.zip`

---

## What's New

✅ **END_USER Form Simplified**
- Single "How is this affecting you?" select (3 plain-language options)
- No Impact/Urgency/Priority sliders for end users
- Auto-maps severity to Impact/Urgency integers

✅ **AGENT+/Dashboard Form Unchanged**
- Full Impact (1-5) and Urgency (1-5) sliders
- Priority dropdown for manual control
- All original functionality preserved

✅ **Copyright Added**
- "© 2026 Srihari Thangavel. All rights reserved." in sidebar footer

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

### Test 1: END_USER Form (Simplified)
1. Log in as END_USER (not SUPER_ADMIN)
2. Navigate to `/home/incidents`
3. Click "New Incident"
4. **Verify:** See only "How is this affecting you?" select (NOT Impact/Urgency/Priority sliders)
5. Select "Significant - it's slowing me down"
6. Fill Title, Description, Category, Location, Phone
7. Click Submit
8. **Verify:** Success toast appears, incident created with Impact=3, Urgency=3

### Test 2: AGENT Form (Full Control)
1. Log in as AGENT or ADMIN
2. Navigate to `/dashboard/incidents`
3. Click "New Incident"
4. **Verify:** See Impact slider, Urgency slider, Priority dropdown (NOT "How is this affecting you?" select)
5. Adjust Impact to 2, Urgency to 4
6. Select Priority and Category
7. Fill other fields and submit
8. **Verify:** Incident created with exact Impact/Urgency values

### Test 3: Copyright Notice
1. Log in as any user
2. Look at left sidebar footer
3. **Verify:** See "© 2026 Srihari Thangavel. All rights reserved."

---

## Troubleshooting

### App Won't Start
1. Check logs: `cat D:\home\LogFiles\Application\spring.log | tail -100`
2. Look for exception stack trace
3. Common issues:
   - Missing environment variables
   - Database connection failed
   - Port binding issue

### Form Not Showing Correctly
1. Clear browser cache (Ctrl+Shift+Delete)
2. Hard refresh (Ctrl+F5)
3. Check browser console (F12) for JavaScript errors

### Rollback
If something breaks:
1. Keep previous app.jar backed up
2. Replace new JAR with old one in Kudu
3. Restart app

---

## Files Modified

**Frontend:**
- `src/main/frontend/src/pages/shared/Incidents.tsx`
  - Added role detection (`isEndUser`)
  - Added severity field and mapping
  - Conditional rendering of form fields
  - Payload transformation before sending

**Layout:**
- `src/main/frontend/src/components/layout/AppLayout.tsx`
  - Added copyright notice to sidebar footer

**Backend:**
- No changes (fully backward compatible)

---

## Verification Checklist

- [ ] App starts without errors
- [ ] END_USER sees simplified form with "How is this affecting you?" select
- [ ] AGENT sees full form with Impact/Urgency/Priority controls
- [ ] END_USER can submit ticket successfully
- [ ] AGENT can submit ticket with full control
- [ ] Copyright notice visible in sidebar
- [ ] All tests still passing (108/108)

---

## Next Steps

1. Deploy app.zip to Kudu (follow steps above)
2. Run post-deployment tests
3. Verify both form types work correctly
4. Report any issues

---

**Ready to deploy?**

**File Location:** `c:\Users\SriHariThangavel\Documents\Dev-IT\v4\target\app.zip`
