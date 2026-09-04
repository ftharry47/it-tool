# Kudu ZipDeploy — Redeploy Steps

## Build Status ✅
- **All 108 tests passed** (0 failures, 1 skipped)
- **app.zip created** at: `c:\Users\SriHariThangavel\Documents\Dev-IT\v4\target\app.zip`

---

## Step 1: Prepare the ZIP File

The `app.zip` is ready in your local build output. You need to upload it to Azure.

**File location:**
```
c:\Users\SriHariThangavel\Documents\Dev-IT\v4\target\app.zip
```

---

## Step 2: Deploy via Kudu ZipDeploy

### Option A: Using Azure Portal (Recommended)

1. Go to **Azure Portal** → **App Service** → **itsm-alignedcardio-fkfkc0fuergxhrat**
2. Click **Advanced Tools** (or go to `https://itsm-alignedcardio-fkfkc0fuergxhrat.scm.azurewebsites.net`)
3. Click **Go →** to open Kudu console
4. In Kudu, go to **Debug Console** → **PowerShell**
5. Navigate to `/home/site/wwwroot`:
   ```powershell
   cd D:\home\site\wwwroot
   ```
6. Delete the old JAR (if exists):
   ```powershell
   Remove-Item app.jar -Force -ErrorAction SilentlyContinue
   ```
7. Upload `app.zip` using the **File Manager** in Kudu:
   - Click **Upload** button
   - Select your local `app.zip`
   - Wait for upload to complete
8. Extract the ZIP in Kudu console:
   ```powershell
   Expand-Archive app.zip -DestinationPath . -Force
   ```
9. Verify the JAR was extracted:
   ```powershell
   ls -Name app.jar
   ```
10. Restart the app service:
    - Go back to Azure Portal
    - Click **Restart** button
    - Wait for app to start (check **Logs** for startup messages)

### Option B: Using PowerShell (Faster)

If you have Azure CLI installed:

```powershell
# Login to Azure
az login

# Deploy the ZIP
az webapp deployment source config-zip --resource-group <YOUR_RESOURCE_GROUP> --name itsm-alignedcardio-fkfkc0fuergxhrat --src "c:\Users\SriHariThangavel\Documents\Dev-IT\v4\target\app.zip"

# Restart the app
az webapp restart --resource-group <YOUR_RESOURCE_GROUP> --name itsm-alignedcardio-fkfkc0fuergxhrat
```

Replace `<YOUR_RESOURCE_GROUP>` with your actual resource group name (e.g., `aligned-cardio-rg`).

---

## Step 3: Verify Deployment

1. **Check app startup logs** in Kudu:
   - Go to **Debug Console** → **PowerShell**
   - View logs:
     ```powershell
     cat D:\home\LogFiles\Application\spring.log | tail -50
     ```
   - Look for `Started Application` message

2. **Test the API**:
   ```powershell
   curl -H "Authorization: Bearer <YOUR_JWT_TOKEN>" https://itsm-alignedcardio-fkfkc0fuergxhrat.canadacentral-01.azurewebsites.net/api/auth/me
   ```
   - Should return your user profile with the correct role

3. **Test in browser**:
   - Go to `https://itsm-alignedcardio-fkfkc0fuergxhrat.canadacentral-01.azurewebsites.net`
   - Sign in with Microsoft
   - Verify you see the correct nav (after running the SQL promotion)

---

## Step 4: Promote Your Account to SUPER_ADMIN

**Before you'll see the ADMIN nav, you must run the SQL promotion queries.**

See `DEPLOYMENT_NOTES.md` for the exact SQL commands.

**Summary:**
1. Find your user by entra_oid (from your JWT)
2. Get the SUPER_ADMIN role ID
3. Delete existing roles and insert SUPER_ADMIN role

After running the SQL, **log out and log back in** to see the updated nav.

---

## Troubleshooting

### App won't start after deployment

1. **Check logs**:
   ```powershell
   cat D:\home\LogFiles\Application\spring.log | tail -100
   ```

2. **Common issues**:
   - Missing environment variables (check Azure App Service settings)
   - Database connection failed (check `SPRING_DATASOURCE_URL`, etc.)
   - Port binding issue (app should bind to port 8080)

3. **Restart the app**:
   ```powershell
   # In Kudu PowerShell
   Stop-Process -Name "java" -Force
   # App will auto-restart
   ```

### Frontend shows blank page

1. Check browser console for errors (F12)
2. Verify MSAL config is correct (check `authConfig.ts`)
3. Check that `/api/auth/me` returns 200 with user data

### 403 on Service Catalog

This means your role is still `END_USER`. Run the SQL promotion and log back in.

---

## Rollback (if needed)

If something breaks, you can quickly rollback:

1. Keep the **previous app.jar** backed up
2. In Kudu, replace the new JAR with the old one
3. Restart the app

---

## Next Steps

After successful deployment:

1. ✅ Run SQL to promote your account to SUPER_ADMIN
2. ✅ Log out and log back in
3. ✅ Verify ADMIN nav appears
4. ✅ Test creating an incident with location, phone, and attachment
5. ✅ Verify the attachment uploads successfully
