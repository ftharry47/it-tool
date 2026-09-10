# Priority 1 Fixes — ISSUE 1 & 5 Complete

## ISSUE 1: Cannot Post Comments (HTTP 400) ✅

### Root Cause
Frontend was sending `{ content, isInternal }` but backend DTO expects `{ body, isPublic }`.
Additionally, the logic was inverted: `isInternal: true` means internal comment, but backend expects `isPublic: boolean` (opposite meaning).

**Frontend Payload (Wrong):**
```json
{ "content": "...", "isInternal": true }
```

**Backend Expected (Correct):**
```json
{ "body": "...", "isPublic": false }
```

### Fix
Modified `IncidentDetail.tsx` line 197 to transform the payload before sending:
```typescript
body: JSON.stringify({ body: payload.content, isPublic: !payload.isInternal })
```

**File:** `IncidentDetail.tsx` (line 197)

**Result:** Comments now POST successfully with correct field names and inverted logic.

---

## ISSUE 5: Projects 500 Error ✅

### Root Cause
`ProjectService.java` was using `NotFoundException` without importing it. This caused a compilation error that resulted in a 500 error at runtime when trying to fetch a project.

**Missing Import:**
```java
import com.alignedcardio.itsm.api.incident.NotFoundException;
```

### Fix
Added the missing import to `ProjectService.java` line 3.

**File:** `ProjectService.java` (line 3)

**Result:** Projects now load successfully without 500 errors.

---

## Build Status

✅ **All 108 tests passed, 0 failures**
✅ **BUILD SUCCESS**
✅ **app.zip ready** at `c:\Users\SriHariThangavel\Documents\Dev-IT\v4\target\app.zip`

---

## What Now Works

- ✅ Comments can be posted (both public and internal)
- ✅ Projects load without 500 errors
- ✅ Project detail page displays correctly

---

## Next: Issues 2-4 (Incident Detail Polish)

Ready to proceed with:
- ISSUE 2: Add attachment download UI with SAS URLs
- ISSUE 3: Fix nav highlighting (active-route matching)
- ISSUE 4: Change Location field to dropdown with site names
