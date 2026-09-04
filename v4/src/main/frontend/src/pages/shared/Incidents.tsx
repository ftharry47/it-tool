import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useMsal, useIsAuthenticated } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Plus, Loader2, AlertCircle, CheckCircle } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { StatusBadge } from '../../components/ui/StatusBadge'

interface Priority { id: string; name: string }
interface Category { id: string; name: string }

const SITE_OPTIONS = [
  'Aligned Cardio Partners - Corporate Office - Richmond, VA',
  'Aligned Interventional Center - Richmond, VA',
  'Aligned Surgical Center - Colonial Heights, VA',
  'Colonial Heart - Williamsburg, VA',
  'Heart Rhythm Associates - Greenville, NC',
  'James River Cardiology - Chesterfield, VA',
  'James River Cardiology - Colonial Heights, VA',
  'James River Cardiology - Discovery, VA',
  'James River Cardiology - Emporia, VA',
  'James River Cardiology - Franklin, VA',
  'James River Cardiology - Lawrenceville, VA',
  'NOVA Cardiovascular Care - Stafford, VA',
  'NOVA Cardiovascular Care - Woodbridge, VA',
  'Potomac Cardiovascular Care - Potomac, VA',
]

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
  
  const isEndUser = currentUser?.roles.includes('END_USER') && !currentUser?.roles.some(r => ['AGENT', 'TEAM_LEAD', 'ADMIN', 'SUPER_ADMIN'].includes(r))
  const severityMap: Record<string, { impact: number; urgency: number }> = {
    low: { impact: 1, urgency: 1 },
    medium: { impact: 3, urgency: 3 },
    high: { impact: 5, urgency: 5 },
  }

  const [form, setForm] = useState({ title: '', description: '', impact: 3, urgency: 3, priorityId: '', categoryId: '', location: '', phone: '', severity: 'medium' })
  const [showForm, setShowForm] = useState(false)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [uploadingAttachment, setUploadingAttachment] = useState(false)
  const [toast, setToast] = useState<{ type: 'error' | 'success'; message: string } | null>(null)

  const listQuery = useQuery<Incident[]>({
    queryKey: ['incidents'],
    enabled: isAuthenticated && !!account,
    queryFn: async () => {
      const endpoint = isEndUser ? '/api/v1/incidents/my' : '/api/v1/incidents'
      const res = await fetchWithToken(instance, account!, endpoint)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
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

  const createMutation = useMutation<Incident, Error, typeof form>({
    mutationFn: async (payload) => {
      const { severity, ...rest } = payload
      const severity_values = isEndUser && severity ? severityMap[severity] : { impact: payload.impact, urgency: payload.urgency }
      const body = {
        ...rest,
        ...severity_values,
        priorityId: (isEndUser ? null : payload.priorityId) || null,
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
      setForm({ title: '', description: '', impact: 3, urgency: 3, priorityId: '', categoryId: '', location: '', phone: '', severity: 'medium' })
      setShowForm(false)
      setToast({ type: 'success', message: `Incident #${newIncident.number} created successfully` })
      setTimeout(() => setToast(null), 3000)
      if (selectedFile) {
        handleAttachmentUpload(newIncident.id)
      }
      setSelectedFile(null)
    },
    onError: (error) => {
      setToast({ type: 'error', message: `Failed to create incident: ${error.message}` })
      setTimeout(() => setToast(null), 5000)
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
    createMutation.mutate(form)
  }

  const isLoading = listQuery.isLoading || prioritiesQuery.isLoading || categoriesQuery.isLoading

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      {toast && (
        <div className={`fixed top-4 right-4 p-4 rounded-lg shadow-lg flex items-center gap-2 ${toast.type === 'error' ? 'bg-red-500 text-white' : 'bg-green-500 text-white'}`}>
          {toast.type === 'error' ? <AlertCircle className="h-5 w-5" /> : <CheckCircle className="h-5 w-5" />}
          <span>{toast.message}</span>
        </div>
      )}
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
            onClick={() => setShowForm(!showForm)}
            className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <Plus className="h-4 w-4" />
            New Incident
          </button>
        </div>

        {showForm && (
          <form onSubmit={handleSubmit} className="space-y-4 rounded-xl border border-border bg-card p-6 shadow-sm">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <label htmlFor="incident-title" className="text-sm font-medium">Title</label>
                <input
                  id="incident-title"
                  name="title"
                  value={form.title}
                  onChange={(e) => setForm({ ...form, title: e.target.value })}
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                  required
                />
              </div>
              <div className="space-y-2 sm:col-span-2">
                <label htmlFor="incident-description" className="text-sm font-medium">Description</label>
                <textarea
                  id="incident-description"
                  name="description"
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                  rows={3}
                />
              </div>
              {isEndUser ? (
                <div className="space-y-2 sm:col-span-2">
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
                  name="location"
                  value={form.location}
                  onChange={(e) => setForm({ ...form, location: e.target.value })}
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                  required
                >
                  <option value="">Select location</option>
                  {SITE_OPTIONS.map((site) => (
                    <option key={site} value={site}>{site}</option>
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
                  placeholder="e.g., +1-555-0123"
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                />
              </div>
              <div className="space-y-2 sm:col-span-2">
                <label htmlFor="incident-attachment" className="text-sm font-medium">Attachment (Optional)</label>
                <input
                  id="incident-attachment"
                  name="attachment"
                  type="file"
                  onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                />
                {selectedFile && <p className="text-xs text-muted-foreground">Selected: {selectedFile.name}</p>}
              </div>
            </div>
            <button
              type="submit"
              disabled={createMutation.isPending || uploadingAttachment}
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              {(createMutation.isPending || uploadingAttachment) && <Loader2 className="h-4 w-4 animate-spin" />}
              {uploadingAttachment ? 'Uploading attachment...' : 'Submit'}
            </button>
          </form>
        )}

        <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
          {isLoading ? (
            <div role="status" aria-live="polite" className="py-12 text-center text-muted-foreground">Loading incidents…</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <caption className="sr-only">List of incidents</caption>
                <thead>
                  <tr className="border-b border-border text-left text-muted-foreground">
                    <th scope="col" className="py-2 pr-4 font-medium">Number</th>
                    <th scope="col" className="py-2 pr-4 font-medium">Title</th>
                    <th scope="col" className="py-2 pr-4 font-medium">Status</th>
                    <th scope="col" className="py-2 pr-4 font-medium">Priority</th>
                    <th scope="col" className="py-2 pr-4 font-medium">Category</th>
                    <th scope="col" className="py-2 pr-4 font-medium">Location</th>
                    <th scope="col" className="py-2 pr-4 font-medium">Phone</th>
                    <th scope="col" className="py-2 pr-4 font-medium">Requester</th>
                    <th scope="col" className="py-2 pr-4 font-medium">Assignee</th>
                  </tr>
                </thead>
                <tbody>
                  {listQuery.data?.length === 0 && (
                    <tr>
                      <td colSpan={9} className="py-8 text-center text-muted-foreground">
                        No incidents yet.
                      </td>
                    </tr>
                  )}
                  {listQuery.data?.map((incident) => (
                    <tr
                      key={incident.id}
                      onClick={() => navigate(isEndUser ? `/home/incidents/${incident.id}` : `/dashboard/incidents/${incident.id}`)}
                      className="border-b border-border/50 cursor-pointer last:border-0 transition hover:bg-muted/50"
                    >
                      <td className="py-3 pr-4">{incident.number}</td>
                      <td className="py-3 pr-4 font-medium">{incident.title}</td>
                      <td className="py-3 pr-4">
                        <StatusBadge status={incident.status} />
                      </td>
                      <td className="py-3 pr-4">{incident.priority ?? '—'}</td>
                      <td className="py-3 pr-4">{incident.category ?? '—'}</td>
                      <td className="py-3 pr-4">{incident.location ?? '—'}</td>
                      <td className="py-3 pr-4">{incident.phone ?? '—'}</td>
                      <td className="py-3 pr-4">{incident.requester ?? '—'}</td>
                      <td className="py-3 pr-4">{incident.assignee ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
