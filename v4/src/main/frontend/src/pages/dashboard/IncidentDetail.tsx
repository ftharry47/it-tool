import { useMemo, useState, type ReactNode } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Link2, Paperclip, MessageCircle, Loader2, History, Clock } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { Loading } from '../../components/ui/Loading'
import { StatusBadge, formatStatusLabel } from '../../components/ui/StatusBadge'
import { IncidentEditForm } from './IncidentEditForm'

interface IncidentDetail {
  id: string
  number: number
  title: string
  description: string
  status: string
  priority: string | null
  category: string | null
  location: string | null
  phone: string | null
  requester: string | null
  assignee: string | null
  estimatedMinutes: number | null
  totalLoggedMinutes: number | null
  createdAt: string
  updatedAt: string
}

interface Comment {
  id: string
  author: string
  body: string
  isInternal: boolean
  createdAt: string
}

interface LinkedIncident {
  id: string
  number: number
  title: string
  status: string
}

interface User {
  id: string
  displayName: string
  email: string
}

interface Attachment {
  id: string
  fileName: string
  sizeBytes: number
  contentType?: string
  blobUrl: string
  createdAt: string
}

interface Activity {
  id: string
  type: 'created' | 'status' | 'assigned' | 'comment' | 'attachment' | 'link'
  title: string
  description?: string
  createdAt: string
  icon: ReactNode
}

interface TimeEntry {
  id: string
  timeSpentMinutes: number
  description: string
  loggedBy: string
  loggedAt: string
}

const statusTransitions: Record<string, string[]> = {
  NEW: ['IN_PROGRESS', 'ON_HOLD', 'WAITING_ON_CUSTOMER'],
  IN_PROGRESS: ['ON_HOLD', 'WAITING_ON_CUSTOMER', 'RESOLVED'],
  ON_HOLD: ['IN_PROGRESS', 'WAITING_ON_CUSTOMER'],
  WAITING_ON_CUSTOMER: ['IN_PROGRESS', 'ON_HOLD'],
  RESOLVED: ['CLOSED', 'REOPENED'],
  CLOSED: ['REOPENED'],
  REOPENED: ['IN_PROGRESS', 'ON_HOLD', 'WAITING_ON_CUSTOMER'],
}

export function IncidentDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { instance, accounts } = useMsal()
  const { currentUser } = useAuth()
  const account = accounts[0]

  const isEndUser = currentUser?.roles.includes('END_USER') && !currentUser?.roles.some(r => ['AGENT', 'TEAM_LEAD', 'ADMIN', 'SUPER_ADMIN'].includes(r))
  const isAdminOrSuperAdmin = (currentUser?.roles.includes('ADMIN') || currentUser?.roles.includes('SUPER_ADMIN')) ?? false

  const [statusError, setStatusError] = useState<string | null>(null)
  const [selectedStatus, setSelectedStatus] = useState('')
  const [selectedAssignee, setSelectedAssignee] = useState('')
  const [isEditing, setIsEditing] = useState(false)
  const [commentContent, setCommentContent] = useState('')
  const [commentIsInternal, setCommentIsInternal] = useState(false)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [linkError, setLinkError] = useState<string | null>(null)
  const [selectedLinkIncident, setSelectedLinkIncident] = useState('')
  const [linkDrawerOpen, setLinkDrawerOpen] = useState(false)
  const [logMinutes, setLogMinutes] = useState('')
  const [logDescription, setLogDescription] = useState('')
  const [estimateMinutes, setEstimateMinutes] = useState('')

  const incidentQuery = useQuery<IncidentDetail>({
    queryKey: ['incident', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/incidents/${id}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!id,
  })

  const commentsQuery = useQuery<Comment[]>({
    queryKey: ['incident-comments', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/incidents/${id}/comments`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!id,
  })

  const attachmentsQuery = useQuery<Attachment[]>({
    queryKey: ['incident-attachments', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/incidents/${id}/attachments`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!id,
  })

  const linkedQuery = useQuery<LinkedIncident[]>({
    queryKey: ['incident-links', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/incidents/${id}/links`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!id,
  })

  const usersQuery = useQuery<User[]>({
    queryKey: ['users'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/users')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const prioritiesQuery = useQuery<{ id: string; name: string }[]>({
    queryKey: ['priorities'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/incidents/priorities')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const categoriesQuery = useQuery<{ id: string; name: string }[]>({
    queryKey: ['categories'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/incidents/categories')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const allIncidentsQuery = useQuery<IncidentDetail[]>({
    queryKey: ['incidents-for-linking'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/incidents')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const timeEntriesQuery = useQuery<TimeEntry[]>({
    queryKey: ['incident-time-entries', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/incidents/${id}/time-entries`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!id,
  })

  const statusMutation = useMutation<IncidentDetail, Error, string>({
    mutationFn: async (nextStatus) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/incidents/${id}/status`, {
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
      queryClient.invalidateQueries({ queryKey: ['incident', id] })
      queryClient.invalidateQueries({ queryKey: ['incidents'] })
    },
    onError: (error) => {
      queryClient.invalidateQueries({ queryKey: ['incident', id] })
      queryClient.refetchQueries({ queryKey: ['incident', id] })
      setStatusError(
        error.message === 'CONFLICT'
          ? 'That status transition is not allowed for this incident.'
          : 'The status change failed. Please try again.'
      )
    },
  })

  const assignMutation = useMutation<IncidentDetail, Error, string>({
    mutationFn: async (assigneeId) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/incidents/${id}/assign`, {
        method: 'PATCH',
        body: JSON.stringify({ assigneeId }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setSelectedAssignee('')
      queryClient.invalidateQueries({ queryKey: ['incident', id] })
      queryClient.invalidateQueries({ queryKey: ['incidents'] })
    },
    onError: (error) => {
      setStatusError(`Failed to assign: ${error.message}`)
    },
  })

  const commentMutation = useMutation<Comment, Error, { content: string; isInternal: boolean }>({
    mutationFn: async (payload) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/incidents/${id}/comments`, {
        method: 'POST',
        body: JSON.stringify({ body: payload.content, isPublic: !payload.isInternal }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setCommentContent('')
      setCommentIsInternal(false)
      queryClient.invalidateQueries({ queryKey: ['incident-comments', id] })
    },
    onError: (error) => {
      setStatusError(`Failed to post comment: ${error.message}`)
    },
  })

  const attachmentMutation = useMutation<Attachment, Error, FormData>({
    mutationFn: async (formData) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/incidents/${id}/attachments`, {
        method: 'POST',
        body: formData,
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setSelectedFile(null)
      queryClient.invalidateQueries({ queryKey: ['incident-attachments', id] })
    },
    onError: (error) => {
      setStatusError(`Failed to upload attachment: ${error.message}`)
    },
  })

  const handleDownloadAttachment = async (att: Attachment) => {
    try {
      const res = await fetchWithToken(instance, account!, att.blobUrl)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const blob = await res.blob()
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = att.fileName
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      window.URL.revokeObjectURL(url)
    } catch (error) {
      setStatusError(`Failed to download attachment: ${(error as Error).message}`)
    }
  }

  const linkMutation = useMutation<LinkedIncident, Error, string>({
    mutationFn: async (linkedIncidentId) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/incidents/${id}/links`, {
        method: 'POST',
        body: JSON.stringify({ linkedIncidentId, linkType: 'RELATED' }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setLinkError(null)
      setSelectedLinkIncident('')
      setLinkDrawerOpen(false)
      queryClient.invalidateQueries({ queryKey: ['incident-links', id] })
    },
    onError: (error) => {
      setLinkError(`Failed to link incident: ${error.message}`)
    },
  })

  const logTimeMutation = useMutation<IncidentDetail, Error, { minutes: number; description: string }>({
    mutationFn: async (payload) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/incidents/${id}/time`, {
        method: 'POST',
        body: JSON.stringify({ timeSpentMinutes: payload.minutes, description: payload.description }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setLogMinutes('')
      setLogDescription('')
      queryClient.invalidateQueries({ queryKey: ['incident', id] })
      queryClient.invalidateQueries({ queryKey: ['incident-time-entries', id] })
    },
    onError: (error) => {
      setStatusError(`Failed to log time: ${error.message}`)
    },
  })

  const estimateMutation = useMutation<IncidentDetail, Error, { minutes: number | null }>({
    mutationFn: async (payload) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/incidents/${id}/estimate`, {
        method: 'PATCH',
        body: JSON.stringify({ estimatedMinutes: payload.minutes }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setEstimateMinutes('')
      queryClient.invalidateQueries({ queryKey: ['incident', id] })
    },
    onError: (error) => {
      setStatusError(`Failed to update estimate: ${error.message}`)
    },
  })

  const activities = useMemo<Activity[]>(() => {
    const incident = incidentQuery.data
    if (!incident || isEndUser) return []

    const list: Activity[] = []
    list.push({
      id: `${incident.id}-created`,
      type: 'created',
      title: 'Incident created',
      description: `Incident #${incident.number} opened by ${incident.requester || 'Unknown'}`,
      createdAt: incident.createdAt,
      icon: <History className="h-4 w-4" />,
    })

    list.push({
      id: `${incident.id}-status`,
      type: 'status',
      title: `Status updated to ${formatStatusLabel(incident.status)}`,
      createdAt: incident.updatedAt,
      icon: <MessageCircle className="h-4 w-4" />,
    })

    if (incident.assignee) {
      list.push({
        id: `${incident.id}-assigned`,
        type: 'assigned',
        title: `Assigned to ${incident.assignee}`,
        createdAt: incident.updatedAt,
        icon: <Link2 className="h-4 w-4" />,
      })
    }

    ;(commentsQuery.data ?? []).forEach((comment) => {
      list.push({
        id: `comment-${comment.id}`,
        type: 'comment',
        title: `${comment.isInternal ? 'Internal' : 'Public'} comment by ${comment.author}`,
        description: comment.body,
        createdAt: comment.createdAt,
        icon: <MessageCircle className="h-4 w-4" />,
      })
    })

    ;(attachmentsQuery.data ?? []).forEach((attachment) => {
      list.push({
        id: `attachment-${attachment.id}`,
        type: 'attachment',
        title: `Attachment uploaded`,
        description: attachment.fileName,
        createdAt: attachment.createdAt,
        icon: <Paperclip className="h-4 w-4" />,
      })
    })

    return list.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
  }, [incidentQuery.data, commentsQuery.data, attachmentsQuery.data, isEndUser])

  if (incidentQuery.isLoading) return <Loading />
  if (incidentQuery.error) return <ErrorFallback error={incidentQuery.error} message="Could not load incident." onRetry={() => incidentQuery.refetch()} />
  if (!incidentQuery.data) return <Loading />

  const incident = incidentQuery.data
  const legalNextStatuses = statusTransitions[incident.status] ?? []
  const visibleComments = isEndUser
    ? (commentsQuery.data ?? []).filter(c => !c.isInternal)
    : (commentsQuery.data ?? [])

  const linkOptions = (allIncidentsQuery.data ?? [])
    .filter((inc) => inc.id !== id && !(linkedQuery.data ?? []).some((linked) => linked.id === inc.id))
    .map((inc) => ({ value: inc.id, label: `#${inc.number} — ${inc.title}` }))

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-5xl space-y-6">
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate(isEndUser ? '/home/incidents' : '/dashboard/incidents')}
            className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </button>
          <h1 className="text-2xl font-semibold tracking-tight">
            Incident #{incident.number} — {incident.title}
          </h1>
        </div>

        <div className="grid gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">
            {/* Details Section */}
            <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="text-lg font-semibold">Details</h2>
                {isAdminOrSuperAdmin && !isEditing && (
                  <button
                    onClick={() => setIsEditing(true)}
                    className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm font-medium transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    Edit
                  </button>
                )}
                {isAdminOrSuperAdmin && isEditing && (
                  <button
                    onClick={() => {
                      if (window.confirm('Discard your changes?')) {
                        setIsEditing(false)
                      }
                    }}
                    className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm font-medium transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    Cancel
                  </button>
                )}
              </div>

              {isEditing && isAdminOrSuperAdmin ? (
                <IncidentEditForm
                  incidentId={id!}
                  initial={{
                    title: incident.title,
                    description: incident.description,
                    status: incident.status,
                    priorityName: incident.priority,
                    categoryName: incident.category,
                    assigneeName: incident.assignee,
                  }}
                  users={(usersQuery.data ?? []).map((u) => ({ id: u.id, name: u.displayName }))}
                  priorities={prioritiesQuery.data ?? []}
                  categories={categoriesQuery.data ?? []}
                  onSaved={() => {
                    setIsEditing(false)
                    queryClient.invalidateQueries({ queryKey: ['incident', id] })
                  }}
                />
              ) : (
                <dl className="space-y-3 text-sm">
                <div className="flex justify-between">
                  <dt className="font-medium text-muted-foreground">Title</dt>
                  <dd>{incident.title}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="font-medium text-muted-foreground">Description</dt>
                  <dd className="max-w-xs text-right">{incident.description || '—'}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="font-medium text-muted-foreground">Category</dt>
                  <dd>{incident.category || '—'}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="font-medium text-muted-foreground">Location</dt>
                  <dd>{incident.location || '—'}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="font-medium text-muted-foreground">Phone</dt>
                  <dd>{incident.phone || '—'}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="font-medium text-muted-foreground">Requester</dt>
                  <dd>{incident.requester || '—'}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="font-medium text-muted-foreground">Original Estimate</dt>
                  <dd>{incident.estimatedMinutes != null ? `${incident.estimatedMinutes} min` : '—'}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="font-medium text-muted-foreground">Time Logged</dt>
                  <dd>{incident.totalLoggedMinutes != null ? `${incident.totalLoggedMinutes} min` : '—'}</dd>
                </div>
              </dl>
              )}
            </section>

            {/* Time Tracking (AGENT+ only) */}
            {!isEndUser && (
              <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
                <h2 className="mb-4 text-lg font-semibold">Log Work</h2>
                <div className="grid gap-4 md:grid-cols-2">
                  <div className="space-y-2">
                    <label htmlFor="log-minutes" className="text-sm font-medium">Time Spent (minutes)</label>
                    <input
                      id="log-minutes"
                      type="number"
                      min={1}
                      value={logMinutes}
                      onChange={(e) => setLogMinutes(e.target.value)}
                      className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                    />
                  </div>
                  <div className="space-y-2">
                    <label htmlFor="log-description" className="text-sm font-medium">Description</label>
                    <textarea
                      id="log-description"
                      name="log-description"
                      value={logDescription}
                      onChange={(e) => setLogDescription(e.target.value)}
                      placeholder="What was done..."
                      rows={2}
                      className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                    />
                  </div>
                </div>
                <div className="mt-4 flex flex-wrap items-center gap-2">
                  <button
                    onClick={() => {
                      const minutes = parseInt(logMinutes, 10)
                      if (!isNaN(minutes) && minutes > 0) {
                        logTimeMutation.mutate({ minutes, description: logDescription })
                      }
                    }}
                    disabled={!logMinutes || logTimeMutation.isPending}
                    className="inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    {logTimeMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    Log Work
                  </button>
                  <input
                    type="number"
                    min={0}
                    placeholder="Set original estimate"
                    value={estimateMinutes}
                    onChange={(e) => setEstimateMinutes(e.target.value)}
                    className="w-40 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                  />
                  <button
                    onClick={() => {
                      const minutes = estimateMinutes ? parseInt(estimateMinutes, 10) : null
                      estimateMutation.mutate({ minutes })
                    }}
                    disabled={estimateMutation.isPending}
                    className="inline-flex items-center rounded-md bg-secondary px-4 py-2 text-sm font-medium transition hover:bg-secondary/80 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    {estimateMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    Set Estimate
                  </button>
                </div>

                {timeEntriesQuery.isLoading && <p className="mt-4 text-sm text-muted-foreground">Loading work log…</p>}
                {timeEntriesQuery.error && (
                  <p className="mt-4 text-sm text-destructive">Could not load work log.</p>
                )}
                {(timeEntriesQuery.data ?? []).length > 0 && (
                  <div className="mt-6 space-y-2">
                    <h3 className="flex items-center gap-2 text-sm font-medium">
                      <Clock className="h-4 w-4" />
                      Work Log History
                    </h3>
                    {timeEntriesQuery.data!.map((entry) => (
                      <div key={entry.id} className="rounded-lg border border-border/50 bg-background p-3 text-sm">
                        <div className="flex items-center justify-between">
                          <span className="font-medium">{entry.timeSpentMinutes} min</span>
                          <span className="text-xs text-muted-foreground">{new Date(entry.loggedAt).toLocaleString()}</span>
                        </div>
                        {entry.description && (
                          <p className="mt-1 text-muted-foreground">{entry.description}</p>
                        )}
                        {entry.loggedBy && <p className="text-xs text-muted-foreground">{entry.loggedBy}</p>}
                      </div>
                    ))}
                  </div>
                )}
              </section>
            )}

            {/* Status Transition Section (AGENT+ only) */}
            {!isEndUser && legalNextStatuses.length > 0 && (
              <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
                <h2 className="mb-4 text-lg font-semibold">Status Transition</h2>
                <div className="flex flex-wrap items-end gap-4">
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
                  <button
                    onClick={() => selectedStatus && statusMutation.mutate(selectedStatus)}
                    disabled={!selectedStatus || statusMutation.isPending}
                    className="inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    {statusMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    Transition
                  </button>
                </div>
                {statusError && (
                  <div className="mt-4">
                    <ErrorFallback error={new Error(statusError)} message={statusError} onRetry={() => incidentQuery.refetch()} />
                  </div>
                )}
              </section>
            )}

            {/* Assign Section (AGENT+ only) */}
            {!isEndUser && (
              <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
                <h2 className="mb-4 text-lg font-semibold">Assign</h2>
                <div className="flex flex-wrap items-end gap-4">
                  <label htmlFor="assignee" className="text-sm font-medium">Assignee</label>
                  <select
                    id="assignee"
                    value={selectedAssignee}
                    onChange={(e) => setSelectedAssignee(e.target.value)}
                    className="w-48 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                  >
                    <option value="">Select…</option>
                    {(usersQuery.data ?? []).map((u) => (
                      <option key={u.id} value={u.id}>{u.displayName}</option>
                    ))}
                  </select>
                  <button
                    onClick={() => selectedAssignee && assignMutation.mutate(selectedAssignee)}
                    disabled={!selectedAssignee || assignMutation.isPending}
                    className="inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    {assignMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    Assign
                  </button>
                </div>
              </section>
            )}

            {/* Comments Section */}
            <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
              <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold">
                <MessageCircle className="h-5 w-5" />
                Comments
              </h2>
              
              {/* Comment List */}
              <div className="mb-6 space-y-4">
                {visibleComments.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No comments yet.</p>
                ) : (
                  visibleComments.map((comment) => (
                    <div key={comment.id} className="rounded-lg border border-border/50 bg-background p-4">
                      <div className="mb-2 flex items-center justify-between">
                        <span className="text-sm font-medium">{comment.author}</span>
                        {comment.isInternal && (
                          <span className="rounded-full bg-yellow-100 px-2 py-0.5 text-xs font-medium text-yellow-800">Internal</span>
                        )}
                      </div>
                      <p className="text-sm text-foreground">{comment.body}</p>
                      <p className="mt-2 text-xs text-muted-foreground">{new Date(comment.createdAt).toLocaleString()}</p>
                    </div>
                  ))
                )}
              </div>

              {/* Comment Form (AGENT+ can post internal, END_USER can post public) */}
              <form
                onSubmit={(e) => {
                  e.preventDefault()
                  if (commentContent.trim()) {
                    commentMutation.mutate({ content: commentContent, isInternal: commentIsInternal })
                  }
                }}
                className="space-y-4 border-t border-border pt-4"
              >
                <div className="space-y-2">
                  <label htmlFor="comment" className="text-sm font-medium">Add Comment</label>
                  <textarea
                    id="comment"
                    value={commentContent}
                    onChange={(e) => setCommentContent(e.target.value)}
                    placeholder="Type your comment…"
                    rows={3}
                    className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                  />
                </div>
                {!isEndUser && (
                  <label className="flex items-center gap-2 text-sm">
                    <input
                      type="checkbox"
                      checked={commentIsInternal}
                      onChange={(e) => setCommentIsInternal(e.target.checked)}
                      className="rounded border border-input"
                    />
                    <span>Internal comment (not visible to requester)</span>
                  </label>
                )}
                <button
                  type="submit"
                  disabled={!commentContent.trim() || commentMutation.isPending}
                  className="inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  {commentMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  Post Comment
                </button>
              </form>
            </section>

            {/* Attachments Section */}
            <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
              <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold">
                <Paperclip className="h-5 w-5" />
                Attachments
              </h2>

              {/* Attachment List */}
              {(attachmentsQuery.data ?? []).length > 0 && (
                <div className="mb-6 space-y-2">
                  {attachmentsQuery.data!.map((att) => (
                    <div key={att.id} className="flex items-center justify-between rounded-lg border border-border/50 bg-background p-3">
                      <div className="text-sm">
                        <p className="font-medium">{att.fileName}</p>
                        <p className="text-xs text-muted-foreground">{(att.sizeBytes / 1024).toFixed(2)} KB</p>
                      </div>
                      <div className="flex items-center gap-3">
                        <button
                          type="button"
                          onClick={() => handleDownloadAttachment(att)}
                          className="text-xs font-medium text-primary hover:underline"
                        >
                          Download
                        </button>
                        <p className="text-xs text-muted-foreground">{new Date(att.createdAt).toLocaleString()}</p>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Upload Form */}
              <form
                onSubmit={(e) => {
                  e.preventDefault()
                  if (selectedFile) {
                    const formData = new FormData()
                    formData.append('file', selectedFile)
                    attachmentMutation.mutate(formData)
                  }
                }}
                className="space-y-4 border-t border-border pt-4"
              >
                <div className="space-y-2">
                  <label htmlFor="attachment" className="text-sm font-medium">Upload Attachment</label>
                  <input
                    id="attachment"
                    type="file"
                    onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
                    className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                  />
                  {selectedFile && <p className="text-xs text-muted-foreground">Selected: {selectedFile.name}</p>}
                </div>
                <button
                  type="submit"
                  disabled={!selectedFile || attachmentMutation.isPending}
                  className="inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  {attachmentMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  Upload
                </button>
              </form>
            </section>

            {/* Linked Incidents Section (AGENT+ only) */}
            {!isEndUser && (
              <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
                <div className="mb-4 flex items-center justify-between">
                  <h2 className="flex items-center gap-2 text-lg font-semibold">
                    <Link2 className="h-5 w-5" />
                    Linked Incidents
                  </h2>
                  <button
                    onClick={() => setLinkDrawerOpen(!linkDrawerOpen)}
                    className="text-sm text-primary hover:underline"
                  >
                    + Link Incident
                  </button>
                </div>

                {/* Link List */}
                {(linkedQuery.data ?? []).length > 0 && (
                  <div className="mb-4 space-y-2">
                    {linkedQuery.data!.map((linked) => (
                      <div key={linked.id} className="flex items-center justify-between rounded-lg border border-border/50 bg-background p-3">
                        <div className="text-sm">
                          <p className="font-medium">#{linked.number} — {linked.title}</p>
                          <p className="text-xs text-muted-foreground">{linked.status}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                {/* Link Form */}
                {linkDrawerOpen && (
                  <form
                    onSubmit={(e) => {
                      e.preventDefault()
                      if (selectedLinkIncident) {
                        linkMutation.mutate(selectedLinkIncident)
                      }
                    }}
                    className="space-y-4 border-t border-border pt-4"
                  >
                    <div className="space-y-2">
                      <label htmlFor="link-incident" className="text-sm font-medium">Select Incident to Link</label>
                      <select
                        id="link-incident"
                        value={selectedLinkIncident}
                        onChange={(e) => setSelectedLinkIncident(e.target.value)}
                        className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                      >
                        <option value="">Select…</option>
                        {linkOptions.map((opt) => (
                          <option key={opt.value} value={opt.value}>{opt.label}</option>
                        ))}
                      </select>
                    </div>
                    <div className="flex gap-2">
                      <button
                        type="submit"
                        disabled={!selectedLinkIncident || linkMutation.isPending}
                        className="inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      >
                        {linkMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                        Link
                      </button>
                      <button
                        type="button"
                        onClick={() => setLinkDrawerOpen(false)}
                        className="inline-flex items-center rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      >
                        Cancel
                      </button>
                    </div>
                    {linkError && <p className="text-sm text-destructive">{linkError}</p>}
                  </form>
                )}
              </section>
            )}
          </div>

          {/* Sidebar */}
          <aside className="space-y-6">
            <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="text-lg font-semibold">Status</h2>
                <StatusBadge status={incident.status} />
              </div>
              <dl className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Priority</dt>
                  <dd className="font-medium">{incident.priority || '—'}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Assignee</dt>
                  <dd className="font-medium">{incident.assignee || '—'}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Created</dt>
                  <dd className="text-xs">{new Date(incident.createdAt).toLocaleString()}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Updated</dt>
                  <dd className="text-xs">{new Date(incident.updatedAt).toLocaleString()}</dd>
                </div>
              </dl>
            </div>

            {!isEndUser && (
              <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
                <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold">
                  <History className="h-5 w-5" />
                  Activity Timeline
                </h2>
                {activities.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No activity yet.</p>
                ) : (
                  <ol className="relative space-y-8 border-l border-border pl-6">
                    {activities.map((activity) => (
                      <li key={activity.id} className="relative">
                        <span className="absolute -left-[2.25rem] flex h-5 w-5 items-center justify-center rounded-full bg-muted ring-4 ring-card">
                          {activity.icon}
                        </span>
                        <div className="space-y-2">
                          <p className="text-sm font-medium text-foreground">{activity.title}</p>
                          {activity.description && (
                            <p className="line-clamp-2 text-xs text-muted-foreground">{activity.description}</p>
                          )}
                          <p className="text-xs text-muted-foreground">{new Date(activity.createdAt).toLocaleString() === 'Invalid Date' ? '—' : new Date(activity.createdAt).toLocaleString()}</p>
                        </div>
                      </li>
                    ))}
                  </ol>
                )}
              </div>
            )}
          </aside>
        </div>
      </div>
    </div>
  )
}
