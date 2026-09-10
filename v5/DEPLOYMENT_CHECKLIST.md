# Deployment Checklist — Bug Fixes Ready

## Pre-Deployment
- [x] Build completed successfully (108 tests passed)
- [x] app.zip created at: `target/app.zip`
- [x] All 5 bugs fixed in code
- [x] Comprehensive test plan provided

## Deployment Steps (Kudu ZipDeploy)

### 1. Open Kudu Console
```
https://itsm-alignedcardio-fkfkc0fuergxhrat.scm.azurewebsites.net
```
Click **Debug Console** → **PowerShell**

### 2. Navigate to wwwroot
```powershell
cd D:\home\site\wwwroot
```

### 3. Clean Old JAR
```powershell
Remove-Item app.jar -Force -ErrorAction SilentlyContinue
```

### 4. Upload app.zip
- Click **Upload** button in File Manager
- Select: `c:\Users\SriHariThangavel\Documents\Dev-IT\v4\target\app.zip`
- Wait for upload to complete

### 5. Extract ZIP
```powershell
Expand-Archive app.zip -DestinationPath . -Force
```

### 6. Verify JAR Exists
```powershell
ls -Name app.jar
```
Should output: `app.jar`

### 7. Restart App (Azure Portal)
- Go to Azure Portal
- Select App Service: `itsm-alignedcardio-fkfkc0fuergxhrat`
- Click **Restart** button
- Wait 30-60 seconds

### 8. Verify Startup (Kudu)
```powershell
cat D:\home\LogFiles\Application\spring.log | tail -50
```
Look for: `Started Application` (no errors)

## Post-Deployment Testing

### Quick Smoke Test (5 minutes)
1. Navigate to: `https://itsm-alignedcardio-fkfkc0fuergxhrat.canadacentral-01.azurewebsites.net`
2. Sign in with Microsoft
3. Verify you see ADMIN nav (Dashboard, Incidents, Problems, Changes, Service Requests, KB, Projects, Reports, Admin, Users, Catalog, Workflows, Automation)
4. Click **Incidents** → **New Incident**
5. Verify form has: Title, Description, Impact slider, Urgency slider, Priority dropdown, Category dropdown, Location, Phone, Attachment
6. Fill form and click Submit
7. Verify success toast appears (green notification top-right)
8. Verify incident appears in list

### Comprehensive Testing (30 minutes)
Follow: `COMPREHENSIVE_TEST_PLAN.md`
- Test all 19 nav items across 3 roles
- Check network tab for 500 errors on Project Detail and Issue Detail
- Report any failures

## If Deployment Fails

### App Won't Start
1. Check logs: `cat D:\home\LogFiles\Application\spring.log | tail -100`
2. Look for exception stack trace
3. Common issues:
   - Missing environment variables
   - Database connection failed
   - Port binding issue

### Rollback
1. Keep previous app.jar backed up
2. Replace new JAR with old one in Kudu
3. Restart app

## Files Ready for Deployment
- **app.zip:** `c:\Users\SriHariThangavel\Documents\Dev-IT\v4\target\app.zip`

## Documentation
- **REDEPLOY_STEPS_BUG_FIXES.md** — Detailed deployment guide
- **COMPREHENSIVE_TEST_PLAN.md** — Full testing checklist (19 items)
- **BUG_FIXES_SUMMARY.md** — What was fixed

---

**Status:** ✅ Ready to deploy

**Next:** Upload app.zip to Kudu and restart app
