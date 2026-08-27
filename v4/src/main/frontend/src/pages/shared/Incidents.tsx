import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useMsal, useIsAuthenticated } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Plus, Loader2 } from 'lucide-react'
import { fetchWithToken } from '../../api/client'

interface Priority { id: string; name: string }
interface Category { id: string; name: string }
interface Incident {
  id: string
  number: number
  title: string
  status: string
  priority: string | null
  category: string | null
  requester: string | null
  assignee: string | null
  createdAt: string
}

export function Incidents() {
  const { instance, accounts } = useMsal()
  const isAuthenticated = useIsAuthenticated()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const account = accounts[0]
  const homeRoute = location.pathname.startsWith('/home') ? '/home' : '/dashboard'

  const [form, setForm] = useState({ title: '', description: '', priorityId: '', categoryId: '' })
  const [showForm, setShowForm] = useState(false)

  const listQuery = useQuery<Incident[]>({
    queryKey: ['incidents'],
    enabled: isAuthenticated && !!account,
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/incidents')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const prioritiesQuery = useQuery<Priority[]>({
    queryKey: ['priorities'],
    enabled: isAuthenticated && !!account,
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/incidents/priorities')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const categoriesQuery = useQuery<Category[]>({
    queryKey: ['categories'],
    enabled: isAuthenticated && !!account,
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/incidents/categories')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const createMutation = useMutation<Incident, Error, typeof form>({
    mutationFn: async (payload) => {
      const res = await fetchWithToken(instance, account!, '/api/incidents', {
        method: 'POST',
        body: JSON.stringify(payload),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['incidents'] })
      setForm({ title: '', description: '', priorityId: '', categoryId: '' })
      setShowForm(false)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    createMutation.mutate(form)
  }

  const isLoading = listQuery.isLoading || prioritiesQuery.isLoading || categoriesQuery.isLoading

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
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
              <div className="space-y-2">
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
            </div>
            <button
              type="submit"
              disabled={createMutation.isPending}
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              {createMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
              Submit
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
                    <th scope="col" className="py-2 pr-4 font-medium">Requester</th>
                    <th scope="col" className="py-2 pr-4 font-medium">Assignee</th>
                  </tr>
                </thead>
                <tbody>
                  {listQuery.data?.length === 0 && (
                    <tr>
                      <td colSpan={7} className="py-8 text-center text-muted-foreground">
                        No incidents yet.
                      </td>
                    </tr>
                  )}
                  {listQuery.data?.map((incident) => (
                    <tr key={incident.id} className="border-b border-border/50 last:border-0">
                      <td className="py-3 pr-4">{incident.number}</td>
                      <td className="py-3 pr-4 font-medium">{incident.title}</td>
                      <td className="py-3 pr-4">
                        <span className="rounded-full bg-secondary px-2 py-0.5 text-xs text-secondary-foreground">
                          {incident.status}
                        </span>
                      </td>
                      <td className="py-3 pr-4">{incident.priority ?? '—'}</td>
                      <td className="py-3 pr-4">{incident.category ?? '—'}</td>
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
