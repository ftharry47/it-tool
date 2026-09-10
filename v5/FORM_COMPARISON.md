# Form Comparison — END_USER vs AGENT+

## END_USER Form (/home/incidents)

```
┌─────────────────────────────────────────┐
│ New Incident                            │
├─────────────────────────────────────────┤
│                                         │
│ Title *                                 │
│ [____________________________________]  │
│                                         │
│ Description                             │
│ [____________________________________]  │
│ [____________________________________]  │
│ [____________________________________]  │
│                                         │
│ How is this affecting you? *            │
│ [v] Significant - it's slowing me down  │
│     - Minor - I can work around it      │
│     - Significant - it's slowing me...  │
│     - Critical - I cannot work at all   │
│                                         │
│ Category *                              │
│ [v] Network                             │
│                                         │
│ Location                                │
│ [____________________________________]  │
│                                         │
│ Phone Number                            │
│ [____________________________________]  │
│                                         │
│ Attachment (Optional)                   │
│ [Choose File] No file chosen            │
│                                         │
│ [Submit]                                │
│                                         │
└─────────────────────────────────────────┘

Fields: 8 (Title, Description, Severity, Category, Location, Phone, Attachment, Submit)
Hidden: Impact, Urgency, Priority (auto-mapped from Severity)
```

---

## AGENT+/Dashboard Form (/dashboard/incidents)

```
┌─────────────────────────────────────────┐
│ New Incident                            │
├─────────────────────────────────────────┤
│                                         │
│ Title *                                 │
│ [____________________________________]  │
│                                         │
│ Description                             │
│ [____________________________________]  │
│ [____________________________________]  │
│ [____________________________________]  │
│                                         │
│ Impact (1-5)                            │
│ [●─────────────────] Medium             │
│                                         │
│ Urgency (1-5)                           │
│ [●─────────────────] Medium             │
│                                         │
│ Priority *                              │
│ [v] Medium                              │
│     - Low                               │
│     - Medium                            │
│     - High                              │
│                                         │
│ Category *                              │
│ [v] Network                             │
│                                         │
│ Location                                │
│ [____________________________________]  │
│                                         │
│ Phone Number                            │
│ [____________________________________]  │
│                                         │
│ Attachment (Optional)                   │
│ [Choose File] No file chosen            │
│                                         │
│ [Submit]                                │
│                                         │
└─────────────────────────────────────────┘

Fields: 11 (Title, Description, Impact, Urgency, Priority, Category, Location, Phone, Attachment, Submit)
Visible: All technical fields for agent control
```

---

## Severity Mapping

| END_USER Selection | Impact | Urgency | Priority (Auto) |
|-------------------|--------|---------|-----------------|
| Minor - I can work around it | 1 | 1 | Low |
| Significant - it's slowing me down | 3 | 3 | Medium |
| Critical - I cannot work at all | 5 | 5 | High |

---

## Key Differences

| Aspect | END_USER | AGENT+ |
|--------|----------|--------|
| **Severity/Impact/Urgency** | Single "How is this affecting you?" select | Impact slider (1-5) + Urgency slider (1-5) |
| **Priority** | Hidden (auto-mapped) | Visible dropdown (manual control) |
| **Complexity** | Simple, 3 options | Full control, 25+ combinations |
| **Use Case** | Self-service ticket submission | Incident triage & management |
| **Adjustment** | Agents can change after creation | Set correctly at creation |

---

## Implementation Details

### Form State
```typescript
const [form, setForm] = useState({
  title: '',
  description: '',
  impact: 3,
  urgency: 3,
  priorityId: '',
  categoryId: '',
  location: '',
  phone: '',
  severity: 'medium',  // END_USER only
})
```

### Role Detection
```typescript
const isEndUser = currentUser?.roles.includes('END_USER') && 
  !currentUser?.roles.some(r => ['AGENT', 'TEAM_LEAD', 'ADMIN', 'SUPER_ADMIN'].includes(r))
```

### Payload Transformation
```typescript
// Before sending to backend:
const { severity, ...rest } = payload
const severity_values = isEndUser && severity ? severityMap[severity] : { impact: payload.impact, urgency: payload.urgency }
const body = { ...rest, ...severity_values }

// Backend receives: { title, description, impact, urgency, categoryId, location, phone, ... }
// (severity field is never sent to backend)
```

---

## Testing Scenarios

### Scenario 1: END_USER Creates "Critical" Ticket
1. Log in as END_USER
2. Navigate to `/home/incidents`
3. Select "Critical - I cannot work at all"
4. Submit
5. **Expected:** Incident created with Impact=5, Urgency=5, Priority=High

### Scenario 2: AGENT Adjusts Ticket
1. Log in as AGENT
2. Navigate to `/dashboard/incidents`
3. Click on the ticket from Scenario 1
4. Adjust Impact to 2, Urgency to 4
5. Submit
6. **Expected:** Incident updated with Impact=2, Urgency=4, Priority recalculated

### Scenario 3: AGENT Creates Ticket Directly
1. Log in as AGENT
2. Navigate to `/dashboard/incidents`
3. Set Impact=2, Urgency=5, Priority=High
4. Submit
5. **Expected:** Incident created with exact values specified

---

## Rollback Plan

If needed, revert to the old form:
1. Remove the `isEndUser` check
2. Always show Impact/Urgency/Priority sliders
3. Remove severity field from form state
4. Redeploy

---

**Status:** ✅ Ready to deploy
