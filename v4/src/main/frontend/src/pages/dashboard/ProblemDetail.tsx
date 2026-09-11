import { useMemo, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, History, Link2, MessageCircle } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { useDocumentTitle } from '../../components/layout/useDocumentTitle'
import { ActivityTimeline, type Activity, type AuditEntry, auditTitle, auditDescription } from '../../components/ui/ActivityTimeline'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { DataTable } from '../../components/ui/DataTable'
import { EntityForm } from '../../components/ui/EntityForm'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { FormDrawer } from '../../components/ui/FormDrawer'
import { Loading } from '../../components/ui/Loading'
import { StatusBadge, formatStatusLabel } from '../../components/ui/StatusBadge'

interface Problem {
  id: string
  number: number
  title: string
  description: string
  status: string
  rootCause: string | null
  workaround: string | null
  assigneeName: string | null
  createdAt: string
  updatedAt: string
}

interface LinkedIncident {
  id: string
  number: number
  title: string
  status: string
}

interface IncidentOption {
  id: string
  number: number
  title: string
}

const statusTransitions: Record<string, string[]> = {
  NEW: ['INVESTIGATING'],
  INVESTIGATING: ['KNOWN_ERROR'],
  'KNOWN ERROR': ['RESOLVED'],
  KNOWN_ERROR: ['RESOLVED'],
  RESOLVED: ['CLOSED', 'INVESTIGATING'],
  CLOSED: [],
}

export function ProblemDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { instance, accounts } = useMsal()
  const { currentUser } = useAuth()
  const account = accounts[0]

  const [statusError, setStatusError] = useState<string | null>(null)
  const [selectedStatus, setSelectedStatus] = useState('')
  const [linkError, setLinkError] = useState<string | null>(null)
  const [linkDrawerOpen, setLinkDrawerOpen] = useState(false)
  const [linkForm, setLinkForm] = useState({ incidentId: '' })
  const [confirmBack, setConfirmBack] = useState(false)
  const [editForm, setEditForm] = useState({ title: '', description: '', rootCause: '', workaround: '' })

  const problemQuery = useQuery<Problem>({
    queryKey: ['problem', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/problems/${id}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      setEditForm({
        title: data.title ?? '',
        description: data.description ?? '',
        rootCause: data.rootCause ?? '',
        workaround: data.workaround ?? '',
      })
      return data
    },
    enabled: !!id && !!account,
  })

  useDocumentTitle(
    problemQuery.data ? `Problem #${problemQuery.data.number}` : 'Problem Detail'
  )

  const linkedQuery = useQuery<LinkedIncident[]>({
    queryKey: ['problem-incidents', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/problems/${id}/incidents`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!id && !!account,
  })

  const allIncidentsQuery = useQuery<IncidentOption[]>({
    queryKey: ['incidents'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/incidents')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const activityQuery = useQuery<AuditEntry[]>({
    queryKey: ['problem-activity', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/problems/${id}/activity`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!id && !!account,
  })

  const editMutation = useMutation<Problem, Error, Record<string, string>>({
    mutationFn: async (payload) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/problems/${id}`, {
        method: 'PATCH',
        body: JSON.stringify(payload),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['problem', id] })
      queryClient.invalidateQueries({ queryKey: ['problem-activity', id] })
      queryClient.invalidateQueries({ queryKey: ['problems'] })
    },
  })

  const statusMutation = useMutation<Problem, Error, string>({
    mutationFn: async (nextStatus) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/problems/${id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status: nextStatus }),
      })
      if (!res.ok) {
        if (res.status === 409) throw new Error('CONFLICT')
        throw new Error(`HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      setStatusError(null)
      setSelectedStatus('')
      queryClient.invalidateQueries({ queryKey: ['problem', id] })
      queryClient.invalidateQueries({ queryKey: ['problem-activity', id] })
      queryClient.invalidateQueries({ queryKey: ['problems'] })
    },
    onError: (error) => {
      // Refetch so the second agent sees the current state before picking a next action.
      queryClient.invalidateQueries({ queryKey: ['problem', id] })
      queryClient.refetchQueries({ queryKey: ['problem', id] })
      setStatusError(
        error.message === 'CONFLICT'
          ? 'That status transition is not allowed for this problem.'
          : 'The status change failed. Please try again.'
      )
    },
  })

  const linkMutation = useMutation<Problem, Error, string>({
    mutationFn: async (incidentId) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/problems/${id}/link-incident`, {
        method: 'POST',
        body: JSON.stringify({ incidentId }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setLinkError(null)
      setLinkForm({ incidentId: '' })
      setLinkDrawerOpen(false)
      queryClient.invalidateQueries({ queryKey: ['problem-incidents', id] })
      queryClient.invalidateQueries({ queryKey: ['problem-activity', id] })
    },
    onError: (error) => {
      setLinkError('Could not link incident. ' + error.message)
    },
  })

  const activities = useMemo<Activity[]>(() => {
    if (!problemQuery.data) return []
    const problem = problemQuery.data
    const list: Activity[] = []
    list.push({
      id: `${problem.id}-created`,
      title: 'Problem created',
      description: `Problem #${problem.number} opened`,
      createdAt: problem.createdAt,
      icon: <History className="h-4 w-4" />,
    })
    list.push({
      id: `${problem.id}-status`,
      title: `Status updated to ${problem.status}`,
      createdAt: problem.updatedAt,
      icon: <MessageCircle className="h-4 w-4" />,
    })
    if (problem.assigneeName) {
      list.push({
        id: `${problem.id}-assigned`,
        title: `Assigned to ${problem.assigneeName}`,
        actorName: problem.assigneeName,
        createdAt: problem.updatedAt,
        icon: <Link2 className="h-4 w-4" />,
      })
    }
    ;(activityQuery.data ?? []).forEach((entry) => {
      list.push({
        id: `audit-${entry.id}`,
        title: auditTitle(entry),
        description: auditDescription(entry),
        actorName: entry.actorName ?? 'System',
        actorBadge: entry.actorRole ?? undefined,
        createdAt: entry.createdAt,
        icon: <History className="h-4 w-4" />,
      })
    })
    return list.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
  }, [problemQuery.data, activityQuery.data])

  const editFormDirty = useMemo(() => {
    if (!problemQuery.data) return false
    const p = problemQuery.data
    return (
      editForm.title !== (p.title ?? '') ||
      editForm.description !== (p.description ?? '') ||
      editForm.rootCause !== (p.rootCause ?? '') ||
      editForm.workaround !== (p.workaround ?? '')
    )
  }, [editForm, problemQuery.data])

  const handleBack = () => {
    if (editFormDirty) {
      setConfirmBack(true)
    } else {
      navigate('/dashboard/problems')
    }
  }

  if (problemQuery.isLoading) return <Loading />
  if (problemQuery.error) return <ErrorFallback error={problemQuery.error} message="Could not load problem." onRetry={() => problemQuery.refetch()} />
  if (!problemQuery.data) return <Loading />

  const problem = problemQuery.data
  const legalNextStatuses = statusTransitions[problem.status] ?? []

  const editFields = [
    { name: 'title', label: 'Title', type: 'text' as const, required: true },
    { name: 'description', label: 'Description', type: 'textarea' as const },
    { name: 'rootCause', label: 'Root Cause', type: 'textarea' as const },
    { name: 'workaround', label: 'Workaround', type: 'textarea' as const },
  ]

  const linkOptions = (allIncidentsQuery.data ?? [])
    .filter((inc) => !(linkedQuery.data ?? []).some((linked) => linked.id === inc.id))
    .map((inc) => ({ value: inc.id, label: `#${inc.number} — ${inc.title}` }))

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-5xl space-y-6">
        <div className="flex items-center gap-4">
          <button
            onClick={handleBack}
            className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </button>
          <h1 className="text-2xl font-semibold tracking-tight">
            Problem #{problem.number} — {problem.title}
          </h1>
        </div>

        <div className="grid gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">
            <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
              <h2 className="mb-4 text-lg font-semibold">Details</h2>
              <EntityForm
                fields={editFields}
                values={editForm}
                onChange={(name, value) => setEditForm({ ...editForm, [name]: value })}
                onSubmit={(e) => {
                  e.preventDefault()
                  editMutation.mutate(editForm)
                }}
                submitLabel="Save Problem"
                pending={editMutation.isPending}
              />
              {editMutation.error && (
                <p className="mt-4 text-sm text-destructive">{editMutation.error.message}</p>
              )}
            </section>

            {legalNextStatuses.length > 0 && (
              <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
                <h2 className="mb-4 text-lg font-semibold">Status Transition</h2>
                <div className="flex flex-wrap items-end gap-4">
                  <div className="space-y-2">
                    <label htmlFor="next-status" className="text-sm font-medium">Next Status</label>
                    <select
                      id="next-status"
                      value={selectedStatus}
                      onChange={(e) => setSelectedStatus(e.target.value)}
                      className="w-48 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                    >
                      <option value="">Select…</option>
                      {legalNextStatuses.map((s) => (
                        <option key={s} value={s}>{formatStatusLabel(s)}</option>
                      ))}
                    </select>
                  </div>
                  <button
                    onClick={() => selectedStatus && statusMutation.mutate(selectedStatus)}
                    disabled={!selectedStatus || statusMutation.isPending}
                    className="inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    Transition
                  </button>
                </div>
                {statusError && (
                  <div className="mt-4">
                    <ErrorFallback error={new Error(statusError)} message={statusError} onRetry={() => problemQuery.refetch()} />
                  </div>
                )}
              </section>
            )}
          </div>

          <aside className="space-y-6">
            <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="text-lg font-semibold">Status</h2>
                <StatusBadge status={problem.status} />
              </div>
              <dl className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Assignee</dt>
                  <dd>—</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Logged in as</dt>
                  <dd className="text-right">{currentUser?.displayName ?? currentUser?.email}</dd>
                </div>
              </dl>
            </div>

            <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="text-lg font-semibold">Linked Incidents</h2>
                <button
                  onClick={() => setLinkDrawerOpen(true)}
                  className="inline-flex items-center gap-1 rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <Link2 className="h-4 w-4" />
                  Link
                </button>
              </div>

              {linkedQuery.isLoading ? (
                <Loading />
              ) : (
                <DataTable<LinkedIncident>
                  caption="Incidents linked to this problem"
                  columns={[
                    { key: 'number', header: 'Number' },
                    { key: 'title', header: 'Title', render: (row) => <Link to={`/dashboard/incidents`} className="hover:underline">{row.title}</Link> },
                    { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
                  ]}
                  data={linkedQuery.data ?? []}
                  getRowKey={(row) => row.id}
                  emptyText="No linked incidents."
                />
              )}
            </div>

            <ActivityTimeline activities={activities} />
          </aside>
        </div>
      </div>

      <FormDrawer
        open={linkDrawerOpen}
        title="Link an Incident"
        dirty={!!linkForm.incidentId}
        onClose={() => {
          setLinkDrawerOpen(false)
          setLinkError(null)
        }}
      >
        {allIncidentsQuery.isLoading ? (
          <Loading />
        ) : (
          <EntityForm
            fields={[
              {
                name: 'incidentId',
                label: 'Incident',
                type: 'select' as const,
                options: linkOptions,
                required: true,
              },
            ]}
            values={linkForm}
            onChange={(name, value) => setLinkForm({ ...linkForm, [name]: value })}
            onSubmit={(e) => {
              e.preventDefault()
              if (linkForm.incidentId) linkMutation.mutate(linkForm.incidentId)
            }}
            submitLabel="Link Incident"
            pending={linkMutation.isPending}
          />
        )}
        {linkError && <p className="mt-4 text-sm text-destructive">{linkError}</p>}
      </FormDrawer>

      <ConfirmDialog
        open={confirmBack}
        title="Discard unsaved changes?"
        description="You have unsaved changes that will be lost if you leave this page."
        confirmLabel="Discard"
        cancelLabel="Cancel"
        destructive
        onConfirm={() => {
          setConfirmBack(false)
          navigate('/dashboard/problems')
        }}
        onCancel={() => setConfirmBack(false)}
      />
    </div>
  )
}
