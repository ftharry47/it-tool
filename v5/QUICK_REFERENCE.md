# Quick Reference — All Changes at a Glance

## Build & Deploy
```powershell
# Build (already done)
.\package.ps1 -AzureClientId "..." -AzureTenantId "..." -AppBaseUrl "https://..."

# Result: target/app.zip (ready to deploy)
```

## Deploy to Azure (Kudu)
1. Go to Kudu: `https://itsm-alignedcardio-fkfkc0fuergxhrat.scm.azurewebsites.net`
2. Upload `app.zip` via File Manager
3. Extract: `Expand-Archive app.zip -DestinationPath . -Force`
4. Restart app in Azure Portal

## Promote Your Account (SQL)
Run in Azure Query Editor against PostgreSQL:

```sql
-- 1. Find your user
SELECT id FROM app_user WHERE object_id = '<YOUR_ENTRA_OID>' AND org_id = '00000000-0000-0000-0000-000000000001';

-- 2. Get SUPER_ADMIN role ID
SELECT id FROM role WHERE name = 'SUPER_ADMIN' AND org_id = '00000000-0000-0000-0000-000000000001';

-- 3. Assign SUPER_ADMIN (replace <IDs> with actual values)
DELETE FROM user_role WHERE user_id = '<USER_ID>' AND org_id = '00000000-0000-0000-0000-000000000001';
INSERT INTO user_role (id, org_id, user_id, role_id, created_by, updated_by)
VALUES (gen_uuid(), '00000000-0000-0000-0000-000000000001', '<USER_ID>', '<ROLE_ID>', '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000');
```

## Test Checklist
- [ ] App starts successfully (check logs in Kudu)
- [ ] Sign in with Microsoft works
- [ ] ADMIN nav appears (after SQL promotion)
- [ ] Service Catalog returns 200 (not 403)
- [ ] Create incident with location, phone, attachment
- [ ] Attachment uploads successfully

## Files Changed
### Backend
- `src/main/java/com/alignedcardio/itsm/entity/UserRole.java` — Changed role fetch to EAGER
- `src/main/java/com/alignedcardio/itsm/entity/Incident.java` — Added location, phone fields
- `src/main/java/com/alignedcardio/itsm/api/incident/IncidentCreateRequest.java` — Added location, phone
- `src/main/java/com/alignedcardio/itsm/api/incident/IncidentResponse.java` — Added location, phone
- `src/main/java/com/alignedcardio/itsm/service/IncidentService.java` — Map new fields
- `src/main/resources/db/migration/V22__incident_location_phone.sql` — New migration

### Frontend
- `src/main/frontend/src/pages/shared/Incidents.tsx` — Added form fields, table columns, file upload
- `src/main/frontend/src/api/client.ts` — Fixed FormData handling

### Tests
- `src/test/java/com/alignedcardio/itsm/api/auth/AuthControllerTest.java` — New JWT auth test

## Key Points
1. **LazyInitializationException fixed** — Role now eagerly loaded
2. **Role-based nav works** — Frontend reads from `/api/auth/me`
3. **Incident fields added** — location, phone, attachment support
4. **All 108 tests pass** — No regressions
5. **app.zip ready** — Just upload and deploy

## Troubleshooting
| Issue | Solution |
|-------|----------|
| App won't start | Check logs: `cat D:\home\LogFiles\Application\spring.log` |
| Still see END_USER nav | Run SQL promotion and log back in |
| Service Catalog 403 | You need SUPER_ADMIN role (run SQL) |
| Attachment won't upload | Check browser console for errors |
| Blank page after login | Check `/api/auth/me` returns 200 |

## Documentation Files
- `FINAL_SUMMARY.md` — Complete overview of all changes
- `DEPLOYMENT_NOTES.md` — SQL templates and field descriptions
- `KUDU_REDEPLOY_STEPS.md` — Detailed deployment guide
- `QUICK_REFERENCE.md` — This file
