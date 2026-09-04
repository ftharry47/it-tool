# Status Transitions Fix — Step 2 Verification

## Issue Found
Frontend IncidentDetail.tsx had **3 illegal status transitions** that don't exist in the backend IncidentStatusMachine:
1. `IN_PROGRESS → REOPENED` ❌
2. `ON_HOLD → RESOLVED` ❌
3. `REOPENED → ON_HOLD` ❌

## Root Cause
I assumed transitions based on the Incident.Status enum values, not the actual IncidentStatusMachine validation rules.

## Fix Applied
Updated `IncidentDetail.tsx` status transitions map to match **IncidentStatusMachine.java** exactly:

### Before (WRONG)
```typescript
const statusTransitions: Record<string, string[]> = {
  NEW: ['IN_PROGRESS', 'ON_HOLD'],
  IN_PROGRESS: ['ON_HOLD', 'RESOLVED', 'REOPENED'],  // ❌ REOPENED not allowed
  ON_HOLD: ['IN_PROGRESS', 'RESOLVED'],               // ❌ RESOLVED not allowed
  RESOLVED: ['CLOSED', 'REOPENED'],
  CLOSED: ['REOPENED'],
  REOPENED: ['IN_PROGRESS', 'ON_HOLD'],               // ❌ ON_HOLD not allowed
}
```

### After (CORRECT)
```typescript
const statusTransitions: Record<string, string[]> = {
  NEW: ['IN_PROGRESS', 'ON_HOLD'],
  IN_PROGRESS: ['ON_HOLD', 'RESOLVED'],
  ON_HOLD: ['IN_PROGRESS'],
  RESOLVED: ['CLOSED', 'REOPENED'],
  CLOSED: ['REOPENED'],
  REOPENED: ['IN_PROGRESS'],
}
```

## Backend Ground Truth
**File:** `IncidentStatusMachine.java` (lines 10-17)

```java
private static final Map<Incident.Status, Set<Incident.Status>> ALLOWED = Map.ofEntries(
    Map.entry(Incident.Status.NEW, Set.of(Incident.Status.IN_PROGRESS, Incident.Status.ON_HOLD)),
    Map.entry(Incident.Status.IN_PROGRESS, Set.of(Incident.Status.ON_HOLD, Incident.Status.RESOLVED)),
    Map.entry(Incident.Status.ON_HOLD, Set.of(Incident.Status.IN_PROGRESS)),
    Map.entry(Incident.Status.RESOLVED, Set.of(Incident.Status.CLOSED, Incident.Status.REOPENED)),
    Map.entry(Incident.Status.CLOSED, Set.of(Incident.Status.REOPENED)),
    Map.entry(Incident.Status.REOPENED, Set.of(Incident.Status.IN_PROGRESS))
);
```

## Verification
✅ Frontend dropdown now matches backend exactly
✅ No illegal transitions offered to user
✅ Backend will accept all transitions shown in dropdown
✅ If illegal transition attempted (e.g., via direct API call), backend returns 409 Conflict and frontend refetches

## Build Status
✅ All 108 tests passed
✅ BUILD SUCCESS
✅ app.zip ready

## Impact
- **User Experience:** Dropdown only shows legal next statuses
- **Data Integrity:** No illegal transitions possible through UI
- **Error Handling:** 409 Conflict handled gracefully with refetch
