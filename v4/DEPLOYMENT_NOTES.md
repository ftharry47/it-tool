# Deployment Notes — Role Promotion & Incident Fields

## ISSUE 1: Promote Your Account to SUPER_ADMIN

Run these SQL queries in Azure Query Editor or psql against your PostgreSQL database.

### Step 1: Find Your User by entra_oid (Most Reliable)

Decode your JWT token at https://jwt.io and find the `oid` claim, then run:

```sql
SELECT id, email, object_id FROM app_user 
WHERE object_id = '<YOUR_ENTRA_OID_FROM_JWT>' 
AND org_id = '00000000-0000-0000-0000-000000000001';
```

Or search by email if you don't have the JWT:

```sql
SELECT id, email, object_id FROM app_user 
WHERE email ILIKE '%srihari%' 
AND org_id = '00000000-0000-0000-0000-000000000001';
```

**Copy the `id` value from the result.**

### Step 2: Get the SUPER_ADMIN Role ID

```sql
SELECT id FROM role 
WHERE name = 'SUPER_ADMIN' 
AND org_id = '00000000-0000-0000-0000-000000000001';
```

**Copy the `id` value from the result.**

### Step 3: Remove Existing Roles and Assign SUPER_ADMIN

Replace `<YOUR_USER_ID>` and `<SUPER_ADMIN_ROLE_ID>` with actual values from steps 1 & 2:

```sql
DELETE FROM user_role 
WHERE user_id = '<YOUR_USER_ID>' 
AND org_id = '00000000-0000-0000-0000-000000000001';

INSERT INTO user_role (id, org_id, user_id, role_id, created_by, updated_by)
VALUES (
  gen_uuid(), 
  '00000000-0000-0000-0000-000000000001', 
  '<YOUR_USER_ID>', 
  '<SUPER_ADMIN_ROLE_ID>', 
  '00000000-0000-0000-0000-000000000000', 
  '00000000-0000-0000-0000-000000000000'
);
```

After running this, log out and log back in. The frontend will automatically show the ADMIN/SUPER_ADMIN nav.

---

## ISSUE 2: New Incident Fields

### Changes Made:

1. **Flyway Migration V22** — Added `location` and `phone` columns to `incident` table
2. **Incident Entity** — Added `location` and `phone` fields with getters/setters
3. **DTOs** — Updated `IncidentCreateRequest` and `IncidentResponse` to include these fields
4. **Service** — Updated `IncidentService.create()` and `toResponse()` to map these fields
5. **Frontend** — Updated `Incidents.tsx` form to include:
   - Location field (text input)
   - Phone field (tel input)
   - File attachment upload (wired to existing `/api/v1/incidents/{id}/attachments` endpoint)
   - Table columns for location and phone

### Build & Deploy:

```powershell
# From the v4 directory
.\package.ps1 -AzureClientId "<YOUR_CLIENT_ID>" -AzureTenantId "<YOUR_TENANT_ID>" -AppBaseUrl "https://itsm-alignedcardio-fkfkc0fuergxhrat.canadacentral-01.azurewebsites.net"
```

Wait for `BUILD SUCCESS`. Then deploy to Azure using Kudu ZipDeploy (same as before).

---

## Testing Checklist

After deployment:

1. ✅ Log in as SUPER_ADMIN (after running the SQL promotion)
2. ✅ Verify you see the ADMIN/SUPER_ADMIN nav (Dashboard + Admin)
3. ✅ Click "Service Catalog" — should return 200 (no more 403)
4. ✅ Create a new incident with location, phone, and attachment
5. ✅ Verify the incident displays location and phone in the table
6. ✅ Verify the attachment was uploaded (check `/api/v1/incidents/{id}/attachments`)

---

## Notes

- **Attachments**: The backend endpoint `POST /api/v1/incidents/{id}/attachments` already existed from Phase 2. The frontend UI was just missing.
- **Role Caching**: The frontend reads roles from `/api/auth/me` on every login. Once your database role is updated, the next login will show the correct nav.
- **Flyway Migration**: V22 will run automatically on app startup and add the columns to existing incidents (they'll be NULL initially).
