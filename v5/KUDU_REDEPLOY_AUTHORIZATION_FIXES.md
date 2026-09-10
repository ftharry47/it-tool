# Kudu Redeploy — Authorization Fixes Complete

## Build Status
✅ **BUILD SUCCESS** — All 108 tests passed, 0 failures
✅ **app.zip ready** at: `c:\Users\SriHariThangavel\Documents\Dev-IT\v4\target\app.zip`

---

## What's Deployed

### Authorization Fixes
- ✅ Service Catalog now accessible to END_USER (GET /api/v1/catalog-items)
- ✅ Knowledge Base now accessible to END_USER (GET /api/v1/kb)
- ✅ Service Requests now accessible to END_USER (GET /api/v1/service-requests)
- ✅ All GET endpoints for END_USER features now have `isAuthenticated()` authorization
- ✅ All AGENT+-only features remain properly restricted

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

### Test 1: END_USER Can Browse Service Catalog
1. Log in as END_USER
2. Navigate to `/home/catalog`
3. **Verify:** Catalog items load (no 403 error)
4. **Verify:** Can see active catalog items
5. **Verify:** Can click item to view details

### Test 2: END_USER Can Browse Knowledge Base
1. Log in as END_USER
2. Navigate to `/home/kb`
3. **Verify:** Published articles load (no 403 error)
4. **Verify:** Can search articles
5. **Verify:** Can click article to view details

### Test 3: AGENT Can Still Access All Features
1. Log in as AGENT
2. Navigate to `/dashboard/incidents`
3. **Verify:** All incidents shown
4. Navigate to `/dashboard/problems`
5. **Verify:** Problems load
6. Navigate to `/dashboard/changes`
7. **Verify:** Changes load

---

## Verification Checklist

- [ ] App starts without errors
- [ ] END_USER can load Service Catalog
- [ ] END_USER can load Knowledge Base
- [ ] END_USER can load Service Requests
- [ ] AGENT can load all dashboard features
- [ ] No 403 errors on GET endpoints for END_USER features
- [ ] All AGENT+-only features still restricted

---

## Files Modified

| File | Changes |
|------|---------|
| CatalogItemController.java | GET endpoints: `hasAnyRole(...)` → `isAuthenticated()` |
| KnowledgeBaseController.java | GET endpoints: Added `@PreAuthorize("isAuthenticated()")` |
| ServiceRequestController.java | GET list: `hasAnyRole(...)` → `isAuthenticated()` |

---

## Troubleshooting

### App Won't Start
1. Check logs: `cat D:\home\LogFiles\Application\spring.log | tail -100`
2. Look for exception stack trace
3. Common issues: Missing environment variables, database connection failed

### 403 Error Still Appears
1. Clear browser cache (Ctrl+Shift+Delete)
2. Hard refresh (Ctrl+F5)
3. Verify app restarted successfully
4. Check logs for authorization errors

### Rollback
If something breaks:
1. Keep previous app.jar backed up
2. Replace new JAR with old one in Kudu
3. Restart app

---

**Ready to deploy?**

**File Location:** `c:\Users\SriHariThangavel\Documents\Dev-IT\v4\target\app.zip`
