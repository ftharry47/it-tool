import { useState } from 'react'
import { useNavigate, useLocation, Link } from 'react-router-dom'
import { useMsal, useIsAuthenticated } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Plus, Loader2 } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { DataTable } from '../../components/ui/DataTable'
import { BulkActionToolbar } from '../../components/ui/BulkActionToolbar'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { ToastStack, type ToastItem } from '../../components/ui/Toast'
import { FilterBar, FilterSelect, useSessionFilters, enumLabel } from '../../components/ui/FilterBar'
import { isValidPhone, PHONE_ERROR } from '../../lib/phone'

interface Priority { id: string; name: string }
interface Category { id: string; name: string }
interface Location { id: string; name: string }

const INCIDENT_STATUSES = ['NEW', 'IN_PROGRESS', 'ON_HOLD', 'WAITING_ON_CUSTOMER', 'RESOLVED', 'CLOSED', 'REOPENED']
const UNASSIGNED = '__unassigned__'

interface Incident {
  id: string
  number: number
  title: string
  status: string
  priority: string | null
  category: string | null
  requester: string | null
  assignee: string | null
  location: string | null
  phone: string | null
  createdAt: string
}

export function Incidents() {
  const { instance, accounts } = useMsal()
  const isAuthenticated = useIsAuthenticated()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const account = accounts[0]
  const { currentUser } = useAuth()
  const homeRoute = location.pathname.startsWith('/home') ? '/home' : '/dashboard'
  const catalogPath = location.pathname.startsWith('/home') ? '/home/catalog' : '/dashboard/service-requests/new'
  
  const isEndUser = currentUser?.roles.includes('END_USER') && !currentUser?.roles.some(r => ['AGENT', 'TEAM_LEAD', 'ADMIN', 'SUPER_ADMIN'].includes(r))
  const severityMap: Record<string, { impact: number; urgency: number }> = {
    low: { impact: 1, urgency: 1 },
    medium: { impact: 3, urgency: 3 },
    high: { impact: 5, urgency: 5 },
  }

  const [form, setForm] = useState({ title: '', description: '', impact: 3, urgency: 3, priorityId: '', categoryId: '', locationId: '', phone: '', severity: 'medium' })
  const [showForm, setShowForm] = useState(false)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [uploadingAttachment, setUploadingAttachment] = useState(false)
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const [confirmCloseForm, setConfirmCloseForm] = useState(false)
  const [showDeleted, setShowDeleted] = useState(false)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [confirmBulk, setConfirmBulk] = useState<{ open: boolean; action: 'delete' | 'restore' } | null>(null)

  const EMPTY_INCIDENT_FORM = { title: '', description: '', impact: 3, urgency: 3, priorityId: '', categoryId: '', locationId: '', phone: '', severity: 'medium' }
  const isFormDirty = JSON.stringify(form) !== JSON.stringify(EMPTY_INCIDENT_FORM) || selectedFile !== null

  const requestCloseForm = () => {
    if (isFormDirty) setConfirmCloseForm(true)
    else setShowForm(false)
  }

  const { filters, setFilter, clearFilters, activeCount } = useSessionFilters('incident-filters', {
    status: '',
    priority: '',
    category: '',
    location: '',
    assignee: '',
  })

  const pushToast = (type: ToastItem['type'], message: string) => {
    setToasts((prev) => [...prev, { id: crypto.randomUUID(), type, message }])
  }
  const dismissToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }

  const listQuery = useQuery<Incident[]>({
    queryKey: ['incidents', showDeleted],
    enabled: isAuthenticated && !!account,
    queryFn: async () => {
      const base = isEndUser ? '/api/v1/incidents/my' : '/api/v1/incidents'
      const endpoint = `${base}?showDeleted=${showDeleted}`
      const res = await fetchWithToken(instance, account!, endpoint)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const bulkMutation = useMutation<void, Error, void>({
    mutationFn: async () => {
      const ids = Array.from(selectedIds)
      const action = confirmBulk?.action ?? 'delete'
      const url = `/api/v1/bulk/incidents/${action === 'delete' ? 'soft-delete' : 'restore'}`
      const res = await fetchWithToken(instance, account!, url, {
        method: 'POST',
        body: JSON.stringify(ids),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
    },
    onSuccess: () => {
      setSelectedIds(new Set())
      queryClient.invalidateQueries({ queryKey: ['incidents'] })
      pushToast('success', confirmBulk?.action === 'delete' ? 'Selected incidents deleted.' : 'Selected incidents restored.')
      setConfirmBulk(null)
    },
    onError: (err) => {
      pushToast('error', `Bulk ${confirmBulk?.action} failed: ${err.message}`)
      setConfirmBulk(null)
    },
  })

  const prioritiesQuery = useQuery<Priority[]>({
    queryKey: ['priorities'],
    enabled: isAuthenticated && !!account,
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/incidents/priorities')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const categoriesQuery = useQuery<Category[]>({
    queryKey: ['categories'],
    enabled: isAuthenticated && !!account,
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/incidents/categories')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const locationsQuery = useQuery<Location[]>({
    queryKey: ['locations'],
    enabled: isAuthenticated && !!account,
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/locations')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const createMutation = useMutation<Incident, Error, typeof form>({
    mutationFn: async (payload) => {
      const { severity, ...rest } = payload
      const severity_values = isEndUser && severity ? severityMap[severity] : { impact: payload.impact, urgency: payload.urgency }
      const body = {
        ...rest,
        ...severity_values,
        priorityId: (isEndUser ? null : payload.priorityId) || null,
        locationId: payload.locationId || null,
      }
      const res = await fetchWithToken(instance, account!, '/api/v1/incidents', {
        method: 'POST',
        body: JSON.stringify(body),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: (newIncident) => {
      queryClient.invalidateQueries({ queryKey: ['incidents'] })
      setForm({ title: '', description: '', impact: 3, urgency: 3, priorityId: '', categoryId: '', locationId: '', phone: '', severity: 'medium' })
      setShowForm(false)
      pushToast('success', `Incident #${newIncident.number} created successfully`)
      if (selectedFile) {
        handleAttachmentUpload(newIncident.id)
      }
      setSelectedFile(null)
    },
    onError: (error) => {
      console.error('Incident creation failed:', error)
      pushToast('error', 'Something went wrong creating the incident. Please try again or contact IT support.')
    },
  })

  const handleAttachmentUpload = async (incidentId: string) => {
    if (!selectedFile || !account) return
    setUploadingAttachment(true)
    try {
      const formData = new FormData()
      formData.append('file', selectedFile)
      const res = await fetchWithToken(instance, account, `/api/v1/incidents/${incidentId}/attachments`, {
        method: 'POST',
        body: formData,
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      queryClient.invalidateQueries({ queryKey: ['incidents'] })
    } catch (error) {
      console.error('Attachment upload failed:', error)
    } finally {
      setUploadingAttachment(false)
    }
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!isValidPhone(form.phone)) {
      pushToast('error', PHONE_ERROR)
      return
    }
    createMutation.mutate(form)
  }

  const isLoading = listQuery.isLoading || prioritiesQuery.isLoading || categoriesQuery.isLoading || locationsQuery.isLoading

  const incidents = listQuery.data ?? []
  const assigneeOptions = Array.from(new Set(incidents.map((i) => i.assignee).filter((a): a is string => !!a))).sort()
  const filteredIncidents = incidents.filter((i) => {
    if (filters.status && i.status !== filters.status) return false
    if (filters.priority && i.priority !== filters.priority) return false
    if (filters.category && i.category !== filters.category) return false
    if (filters.location && i.location !== filters.location) return false
    // Assignee filter is dashboard-only; ignored on the /home side.
    if (!isEndUser && filters.assignee) {
      if (filters.assignee === UNASSIGNED ? i.assignee !== null : i.assignee !== filters.assignee) return false
    }
    return true
  })

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <ToastStack toasts={toasts} onDismiss={dismissToast} />
      <ConfirmDialog
        open={!!confirmBulk}
        title={confirmBulk?.action === 'delete' ? 'Delete selected incidents?' : 'Restore selected incidents?'}
        description={
          confirmBulk?.action === 'delete'
            ? `Delete ${selectedIds.size} incidents? This can be undone via Restore.`
            : `Restore ${selectedIds.size} incidents? They will be visible again in all views and reports.`
        }
        confirmLabel={confirmBulk?.action === 'delete' ? 'Delete' : 'Restore'}
        destructive={confirmBulk?.action === 'delete'}
        onConfirm={() => bulkMutation.mutate()}
        onCancel={() => setConfirmBulk(null)}
      />
      <ConfirmDialog
        open={confirmCloseForm}
        title="Discard unsaved changes?"
        description="You have unsaved changes in the new incident form that will be lost."
        confirmLabel="Discard"
        destructive
        onConfirm={() => {
          setConfirmCloseForm(false)
          setShowForm(false)
        }}
        onCancel={() => setConfirmCloseForm(false)}
      />
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate(homeRoute)}
            className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </button>
          <h1 className="text-2xl font-semibold tracking-tight">Incidents</h1>
        </div>

        <div className="flex justify-end">
          <button
            onClick={() => (showForm ? requestCloseForm() : setShowForm(true))}
            className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <Plus className="h-4 w-4" />
            New Incident
          </button>
        </div>

        {showForm && (
          <form onSubmit={handleSubmit} className="rounded-xl border border-border bg-card shadow-sm">
            <div className="border-b border-border px-6 py-4">
              <h2 className="text-base font-semibold tracking-tight">New Incident</h2>
              <p className="mt-0.5 text-xs text-muted-foreground">Describe the issue and we'll route it to the right team.</p>
              <p className="mt-1.5 text-xs text-muted-foreground">
                Need a NEW item or replacement (not a repair)?{' '}
                <Link to={catalogPath} className="text-primary underline hover:text-primary/80">Submit a Service Request</Link>{' '}
                instead.
              </p>
            </div>

            <div className="space-y-5 p-6">
              <div className="space-y-2">
                <label htmlFor="incident-title" className="text-sm font-medium">Title</label>
                <input
                  id="incident-title"
                  name="title"
                  value={form.title}
                  onChange={(e) => setForm({ ...form, title: e.target.value })}
                  placeholder="Short summary of the issue"
                  className="w-full rounded-md border border-input bg-background px-3 py-2.5 text-base outline-none focus:ring-2 focus:ring-ring"
                  required
                />
              </div>
              <div className="space-y-2">
                <label htmlFor="incident-description" className="text-sm font-medium">Description</label>
                <textarea
                  id="incident-description"
                  name="description"
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                  placeholder="What happened, when it started, and anything you've already tried"
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                  rows={5}
                />
              </div>

              <div>
                <p className="mb-3 border-b border-border pb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Details</p>
                <div className="grid gap-4 sm:grid-cols-2">
                  {isEndUser ? (
                    <div className="space-y-2">
                      <label htmlFor="incident-severity" className="text-sm font-medium">How is this affecting you?</label>
                      <select
                        id="incident-severity"
                        name="severity"
                        value={form.severity}
                        onChange={(e) => setForm({ ...form, severity: e.target.value })}
                        className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                        required
                      >
                        <option value="low">Minor - I can work around it</option>
                        <option value="medium">Significant - it's slowing me down</option>
                        <option value="high">Critical - I cannot work at all</option>
                      </select>
                    </div>
                  ) : (
                    <div className="space-y-2">
                      <label htmlFor="incident-priority" className="text-sm font-medium">Priority</label>
                      <select
                        id="incident-priority"
                        name="priorityId"
                        value={form.priorityId}
                        onChange={(e) => setForm({ ...form, priorityId: e.target.value })}
                        className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                        required
                      >
                        <option value="">Select priority</option>
                        {prioritiesQuery.data?.map((p) => (
                          <option key={p.id} value={p.id}>{p.name}</option>
                        ))}
                      </select>
                    </div>
                  )}
                  <div className="space-y-2">
                    <label htmlFor="incident-category" className="text-sm font-medium">Category</label>
                    <select
                      id="incident-category"
                      name="categoryId"
                      value={form.categoryId}
                      onChange={(e) => setForm({ ...form, categoryId: e.target.value })}
                      className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                      required
                    >
                      <option value="">Select category</option>
                      {categoriesQuery.data?.map((c) => (
                        <option key={c.id} value={c.id}>{c.name}</option>
                      ))}
                    </select>
                  </div>
                  <div className="space-y-2">
                    <label htmlFor="incident-location" className="text-sm font-medium">Location</label>
                    <select
                      id="incident-location"
                      name="locationId"
                      value={form.locationId}
                      onChange={(e) => setForm({ ...form, locationId: e.target.value })}
                      className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                      required
                    >
                      <option value="">Select location</option>
                      {locationsQuery.data?.map((loc) => (
                        <option key={loc.id} value={loc.id}>{loc.name}</option>
                      ))}
                    </select>
                  </div>
                  <div className="space-y-2">
                    <label htmlFor="incident-phone" className="text-sm font-medium">Phone Number</label>
                    <input
                      id="incident-phone"
                      name="phone"
                      type="tel"
                      value={form.phone}
                      onChange={(e) => setForm({ ...form, phone: e.target.value })}
                      placeholder="e.g., +1 555-012-3456"
                      className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                    />
                  </div>
                </div>
              </div>

              <div>
                <p className="mb-3 border-b border-border pb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Attachment</p>
                <input
                  id="incident-attachment"
                  name="attachment"
                  type="file"
                  onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                />
                {selectedFile && <p className="mt-1 text-xs text-muted-foreground">Selected: {selectedFile.name}</p>}
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 border-t border-border px-6 py-4">
              <button
                type="button"
                onClick={requestCloseForm}
                className="rounded-md border border-border bg-background px-4 py-2 text-sm font-medium transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={createMutation.isPending || uploadingAttachment}
                className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {(createMutation.isPending || uploadingAttachment) && <Loader2 className="h-4 w-4 animate-spin" />}
                {uploadingAttachment ? 'Uploading attachment…' : createMutation.isPending ? 'Submitting…' : 'Submit'}
              </button>
            </div>
          </form>
        )}

        <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <div className="mb-4">
            <FilterBar activeCount={activeCount} onClear={clearFilters}>
              <FilterSelect
                label="Status"
                value={filters.status}
                options={INCIDENT_STATUSES.map((s) => ({ value: s, label: enumLabel(s) }))}
                onChange={(v) => setFilter('status', v)}
              />
              <FilterSelect
                label="Priority"
                value={filters.priority}
                options={(prioritiesQuery.data ?? []).map((p) => ({ value: p.name, label: p.name }))}
                onChange={(v) => setFilter('priority', v)}
              />
              <FilterSelect
                label="Category"
                value={filters.category}
                options={(categoriesQuery.data ?? []).map((c) => ({ value: c.name, label: c.name }))}
                onChange={(v) => setFilter('category', v)}
              />
              <FilterSelect
                label="Location"
                value={filters.location}
                options={(locationsQuery.data ?? []).map((l) => ({ value: l.name, label: l.name }))}
                onChange={(v) => setFilter('location', v)}
              />
              {!isEndUser && (
                <FilterSelect
                  label="Assignee"
                  value={filters.assignee}
                  options={[
                    { value: UNASSIGNED, label: 'Unassigned' },
                    ...assigneeOptions.map((a) => ({ value: a, label: a })),
                  ]}
                  onChange={(v) => setFilter('assignee', v)}
                />
              )}
            </FilterBar>
          </div>
          {!isEndUser && (
            <BulkActionToolbar
              selectedCount={selectedIds.size}
              showDeleted={showDeleted}
              onToggleShowDeleted={() => { setShowDeleted((v) => !v); setSelectedIds(new Set()) }}
              onDelete={() => setConfirmBulk({ open: true, action: 'delete' })}
              onRestore={() => setConfirmBulk({ open: true, action: 'restore' })}
            />
          )}
          {isLoading ? (
            <div role="status" aria-live="polite" className="py-12 text-center text-muted-foreground">Loading incidents…</div>
          ) : (
            <DataTable<Incident>
              caption="List of incidents"
              columns={[
                { key: 'number', header: 'Number' },
                { key: 'title', header: 'Title' },
                { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
                { key: 'priority', header: 'Priority', render: (row) => row.priority ?? '—' },
                { key: 'category', header: 'Category', render: (row) => row.category ?? '—' },
                { key: 'location', header: 'Location', render: (row) => row.location ?? '—' },
                { key: 'phone', header: 'Phone', render: (row) => row.phone ?? '—' },
                { key: 'requester', header: 'Requester', render: (row) => row.requester ?? '—' },
                { key: 'assignee', header: 'Assignee', render: (row) => row.assignee ?? '—' },
              ]}
              data={filteredIncidents}
              getRowKey={(row) => row.id}
              onRowClick={(row) => navigate(isEndUser ? `/home/incidents/${row.id}` : `/dashboard/incidents/${row.id}`)}
              selectable={!isEndUser}
              selectedIds={selectedIds}
              onSelectionChange={setSelectedIds}
              emptyText={incidents.length === 0 ? 'No incidents yet.' : 'No incidents match the selected filters.'}
            />
          )}
        </div>
      </div>
    </div>
  )
}
