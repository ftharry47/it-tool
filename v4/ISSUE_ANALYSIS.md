# Issue Analysis — 4 Critical Issues

## ISSUE 1 — Submitted ticket doesn't appear in "My Incidents" (END_USER)

### Status: **BUG IN EXISTING CODE**

### Root Cause
The `/api/incidents` endpoint (line 29-34 in IncidentController) **lists ALL incidents in the org**, not filtered by reporter_id:

```java
@GetMapping
@PreAuthorize("hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')")
public List<IncidentSummary> list(@AuthenticationPrincipal Jwt jwt) {
    AppUser user = userService.syncFromJwt(jwt);
    return incidentService.list(user.getOrgId());  // ← No reporter_id filter
}
```

**IncidentService.list()** (line 64-68):
```java
public List<IncidentSummary> list(UUID orgId) {
    return incidentRepository.findByOrgIdOrderByCreatedAtDesc(orgId).stream()
            .map(this::toSummary)
            .toList();  // ← Returns ALL incidents for the org
}
```

### Problems
1. **Authorization:** Endpoint is restricted to `hasAnyRole('AGENT','TEAM_LEAD','ADMIN','SUPER_ADMIN')` — **END_USER cannot call it at all** (403)
2. **Filtering:** Even if END_USER could call it, it returns all org incidents, not just their own
3. **Frontend:** Incidents.tsx calls `/api/incidents` for both END_USER and AGENT+ — but END_USER gets 403

### What Should Happen
- **END_USER** should see only incidents they created (reporter_id = current_user.id)
- **AGENT+** should see all incidents in the org
- Two separate endpoints or one endpoint with role-based filtering

### Fix Required
1. Create a new endpoint `/api/incidents/my` (or similar) restricted to `isAuthenticated()` that filters by reporter_id
2. OR: Modify `/api/incidents` to accept a query param `?filter=my` and return filtered results based on role
3. Update frontend to call the correct endpoint based on role

---

## ISSUE 2 — Clicking a ticket does not open the incident detail page

### Status: **MISSING FEATURE (No IncidentDetail component exists)**

### Root Cause
1. **No IncidentDetail component** — Only exists for Problem, Change, ServiceRequest, Issue
2. **No route** — Routes show `/dashboard/incidents` but no `/dashboard/incidents/:id`
3. **No click handler** — Incidents.tsx table rows have no onClick or Link wrapper

### Evidence
**routes/index.tsx** (line 49):
```typescript
{ path: 'dashboard/incidents', label: 'Incidents', element: <Incidents /> },
// ← No :id route for incident detail
```

**Incidents.tsx** (line 342-357):
```typescript
{listQuery.data?.map((incident) => (
  <tr key={incident.id} className="border-b border-border/50 last:border-0">
    <td className="py-3 pr-4">{incident.number}</td>
    <td className="py-3 pr-4 font-medium">{incident.title}</td>
    // ← No onClick, no Link, no routing
    ...
  </tr>
))}
```

### What Should Exist
- `IncidentDetail.tsx` component (like ProblemDetail, ChangeDetail)
- Route: `/dashboard/incidents/:id`
- Click handler on table row to navigate to detail page
- Detail page should show:
  - Incident fields (title, description, status, priority, etc.)
  - Status transition dropdown (validated against IncidentStatusMachine)
  - Assign action (AGENT+ only)
  - Comments (public/internal)
  - Linked incidents
  - Attachment upload/list

### Fix Required
1. Create `IncidentDetail.tsx` component
2. Add route `/dashboard/incidents/:id`
3. Add click handler to table row to navigate
4. Implement full detail view with all fields and actions

---

## ISSUE 3 — "User Admin" is a placeholder, not implemented

### Status: **MISSING FEATURE (Frontend only; backend may exist)**

### Current State
**routes/index.tsx** (line 70):
```typescript
{ path: 'admin/users', label: 'Users', element: <ComingSoon title="User Admin" /> },
```

**ComingSoon component** shows:
```
"This module is defined in the Phase 12 plan and will be implemented in an upcoming sub-phase"
```

### Backend Status
Need to check if these endpoints exist:
- `GET /api/v1/users` — List all users
- `PATCH /api/v1/users/{id}/role` — Update user role
- `POST /api/v1/users` — Create manual account (optional)

**No UserController found** in `/api/auth` or `/api/admin`

### What Should Exist
1. **User List Page** — Display all users with:
   - Name, Email, Role, Status
   - Filter by role
   - Search by name/email
2. **Role Assignment UI** — For each user:
   - Current role badge
   - "Change Role" button/dropdown
   - Confirm dialog
   - Success/error toast
3. **Manual Account Creation** (optional) — For non-Microsoft accounts:
   - Email, Name, Role
   - Create button
   - Success/error toast

### Fix Required
1. Check if backend endpoints exist (likely don't)
2. If not, create:
   - `UserAdminController` with GET /api/v1/users and PATCH /api/v1/users/{id}/role
3. Create `UserAdmin.tsx` component with:
   - User list table
   - Role assignment UI
   - Search/filter

---

## ISSUE 4 — No notification/acknowledgment on ticket submission

### Status: **PARTIALLY IMPLEMENTED (Toast exists; domain event exists; automation may not be configured)**

### Current State
**Incidents.tsx** (line 90-95):
```typescript
onSuccess: (newIncident) => {
    queryClient.invalidateQueries({ queryKey: ['incidents'] })
    setForm({ ... })
    setShowForm(false)
    setToast({ type: 'success', message: `Incident #${newIncident.number} created successfully` })
    // ← Toast notification exists
    setTimeout(() => setToast(null), 3000)
    ...
}
```

**IncidentService.create()** (line 102-110):
```typescript
eventPublisher.publishEvent(new IncidentCreatedEvent(
    incident.getOrgId(),
    incident.getId(),
    Map.of(
        "id", incident.getId(),
        "number", incident.getNumber(),
        "status", incident.getStatus().name(),
        "priority", incident.getPriority().getName(),
        "category", incident.getCategory().getName())));
// ← Domain event is published
```

### What Works
✅ END_USER sees success toast after submission
✅ Domain event (IncidentCreatedEvent) is published
✅ SLA engine is triggered (line 100)

### What's Missing
❌ **Admin/Agent notification** — No automation rule to send notification to admins/agents when new incident is created
❌ **Email notification** — No email sent to requester or admins

### Fix Required
1. **Option A (Recommended):** Create automation rule in UI:
   - Trigger: INCIDENT.CREATED
   - Action: SEND_NOTIFICATION
   - Recipients: ADMIN, AGENT, TEAM_LEAD
   - This leverages the existing Automation Engine (Phase 8)

2. **Option B (Hardcoded):** Create NotificationService listener:
   - Listen for IncidentCreatedEvent
   - Send notification to admins/agents
   - Send confirmation email to requester

**Current Status:** Infrastructure exists (domain events, automation engine), just needs configuration or listener implementation.

---

## Summary Table

| Issue | Type | Severity | Fix Effort | Blocking |
|-------|------|----------|-----------|----------|
| 1: My Incidents filtering | Bug | HIGH | Medium | YES |
| 2: Incident Detail page | Missing Feature | HIGH | Medium | YES |
| 3: User Admin page | Missing Feature | MEDIUM | Medium | NO |
| 4: Notifications | Missing Feature | MEDIUM | Low | NO |

---

## Recommended Fix Order

1. **Issue 1** — Fix `/api/incidents` filtering (blocks END_USER from seeing their tickets)
2. **Issue 2** — Create IncidentDetail page (blocks AGENT from viewing ticket details)
3. **Issue 3** — Create UserAdmin page (blocks ADMIN from managing users)
4. **Issue 4** — Add notification automation (nice-to-have, doesn't block core functionality)

---

## Files to Create/Modify

### Issue 1 Fix
- `IncidentController.java` — Add `/api/incidents/my` endpoint or modify existing
- `IncidentService.java` — Add `listByReporter()` method
- `IncidentRepository.java` — Add `findByOrgIdAndReporterIdOrderByCreatedAtDesc()` query
- `Incidents.tsx` — Call correct endpoint based on role

### Issue 2 Fix
- `IncidentDetail.tsx` — New component (copy from ProblemDetail)
- `routes/index.tsx` — Add `/dashboard/incidents/:id` route
- `Incidents.tsx` — Add click handler to table row

### Issue 3 Fix
- `UserAdminController.java` — New controller with GET /api/v1/users, PATCH /api/v1/users/{id}/role
- `UserAdmin.tsx` — New component with user list and role assignment UI

### Issue 4 Fix
- `AutomationAdmin.tsx` — Pre-create automation rule (or create listener in IncidentService)
- Optional: `NotificationService.java` — Listen for IncidentCreatedEvent

---

**Next Step:** Confirm this analysis is correct, then I'll build the fixes.
