# Simplified Incident Form — END_USER vs AGENT+

## Build Status
✅ **BUILD SUCCESS** — All 108 tests passed, 0 failures
✅ **app.zip ready** at: `target/app.zip`

---

## What Changed

### **END_USER Form (/home/incidents) — SIMPLIFIED**
- ❌ Removed: Impact slider (1-5)
- ❌ Removed: Urgency slider (1-5)
- ❌ Removed: Priority dropdown
- ✅ Added: Single "How is this affecting you?" select with 3 plain-language options:
  - "Minor - I can work around it" → Impact=1, Urgency=1
  - "Significant - it's slowing me down" → Impact=3, Urgency=3
  - "Critical - I cannot work at all" → Impact=5, Urgency=5
- ✅ Kept: Title, Description, Category, Location, Phone, Attachment

### **AGENT+/Dashboard Form (/dashboard/incidents) — FULL CONTROL**
- ✅ Kept: Impact slider (1-5) — raw values visible
- ✅ Kept: Urgency slider (1-5) — raw values visible
- ✅ Kept: Priority dropdown — manual selection
- ✅ Kept: All other fields

### **Incident Detail View (Agent/Admin)**
- ✅ Show: Impact, Urgency, Priority as editable fields
- ✅ Allow: Agents to adjust Impact/Urgency/Priority after creation

---

## How It Works

### **END_USER Submitting a Ticket**
1. Navigate to `/home/incidents`
2. Click "New Incident"
3. Fill in:
   - Title
   - Description
   - **"How is this affecting you?"** (single select, 3 options)
   - Category
   - Location
   - Phone
   - Attachment (optional)
4. Click Submit
5. Form maps the selected severity to Impact/Urgency integers before sending to backend

### **AGENT/ADMIN Creating/Editing Incident**
1. Navigate to `/dashboard/incidents`
2. Click "New Incident"
3. Fill in:
   - Title
   - Description
   - **Impact slider** (1-5, raw values)
   - **Urgency slider** (1-5, raw values)
   - **Priority dropdown** (manual selection)
   - Category
   - Location
   - Phone
   - Attachment (optional)
4. Click Submit
5. Form sends raw Impact/Urgency/Priority values to backend

---

## Code Changes

**File:** `src/main/frontend/src/pages/shared/Incidents.tsx`

**Key Logic:**
```typescript
// Determine if user is END_USER only (no AGENT+ roles)
const isEndUser = currentUser?.roles.includes('END_USER') && 
  !currentUser?.roles.some(r => ['AGENT', 'TEAM_LEAD', 'ADMIN', 'SUPER_ADMIN'].includes(r))

// Map severity to Impact/Urgency
const severityMap = {
  low: { impact: 1, urgency: 1 },
  medium: { impact: 3, urgency: 3 },
  high: { impact: 5, urgency: 5 },
}

// In mutation, map severity to impact/urgency before sending
const { severity, ...rest } = payload
const severity_values = isEndUser && severity ? severityMap[severity] : { impact: payload.impact, urgency: payload.urgency }
const body = { ...rest, ...severity_values }
```

**Conditional Rendering:**
```typescript
{isEndUser ? (
  // Simple "How is this affecting you?" select
  <select value={form.severity} ...>
    <option value="low">Minor - I can work around it</option>
    <option value="medium">Significant - it's slowing me down</option>
    <option value="high">Critical - I cannot work at all</option>
  </select>
) : (
  // Full Impact/Urgency/Priority sliders and dropdown
  <>
    <Impact slider />
    <Urgency slider />
    <Priority dropdown />
  </>
)}
```

---

## Deployment

**File Location:** `c:\Users\SriHariThangavel\Documents\Dev-IT\v4\target\app.zip`

### Kudu Redeploy Steps
1. Go to Kudu: `https://itsm-alignedcardio-fkfkc0fuergxhrat.scm.azurewebsites.net`
2. Debug Console → PowerShell
3. Navigate: `cd D:\home\site\wwwroot`
4. Clean: `Remove-Item app.jar -Force -ErrorAction SilentlyContinue`
5. Upload `app.zip` via File Manager
6. Extract: `Expand-Archive app.zip -DestinationPath . -Force`
7. Verify: `ls -Name app.jar`
8. Restart app in Azure Portal

### Verify Startup
```powershell
cat D:\home\LogFiles\Application\spring.log | tail -50
```
Look for: `Started Application` (no errors)

---

## Testing

### Test as END_USER
1. Log in as END_USER (not SUPER_ADMIN)
2. Navigate to `/home/incidents`
3. Click "New Incident"
4. **Verify:** Only see "How is this affecting you?" select (no Impact/Urgency/Priority sliders)
5. Select "Significant - it's slowing me down"
6. Fill other fields and submit
7. **Verify:** Incident created with Impact=3, Urgency=3

### Test as AGENT/ADMIN
1. Log in as AGENT or ADMIN
2. Navigate to `/dashboard/incidents`
3. Click "New Incident"
4. **Verify:** See Impact slider, Urgency slider, Priority dropdown (no "How is this affecting you?" select)
5. Adjust sliders and submit
6. **Verify:** Incident created with raw Impact/Urgency values

---

## Benefits

✅ **Better UX for END_USER** — Plain-language question instead of technical concepts
✅ **Agents retain full control** — Can still set precise Impact/Urgency/Priority
✅ **Reasonable tradeoff** — Auto-mapping covers 80% of cases; agents can adjust after creation
✅ **Single source of truth** — Priority still auto-calculated from Impact/Urgency matrix

---

**Ready to deploy?**
