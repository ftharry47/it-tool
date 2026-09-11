import { useEffect, useMemo, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Check, CheckCircle2, History, MessageCircle, XCircle } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useDocumentTitle } from '../../components/layout/useDocumentTitle'
import { ActivityTimeline, type Activity, type AuditEntry, auditTitle, auditDescription } from '../../components/ui/ActivityTimeline'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EntityForm } from '../../components/ui/EntityForm'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { FormDrawer } from '../../components/ui/FormDrawer'
import { Loading } from '../../components/ui/Loading'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { formatDateTime, toEasternInputValue } from '../../lib/date'

interface ChangeApproval {
  id: string
  approverId: string
  approverName: string
  sequenceOrder: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  decidedAt: string | null
  comment: string | null
}

interface Change {
  id: string
  number: number
  title: string
  description: string
  changeType: string
  risk: string
  status: string
  requestedById: string | null
  requestedByName: string
  plannedStart: string | null
  plannedEnd: string | null
  rollbackPlan: string
  postImplementationReview: string
  linkedProblemId: string | null
  locationId: string | null
  locationName: string | null
  approvals: ChangeApproval[]
  createdAt: string
}

interface User {
  id: string
  displayName: string
  email: string
}

interface Problem {
  id: string
  number: string
  title: string
}

interface Location {
  id: string
  name: string
  address: string | null
}

const statusTransitions: Record<string, string[]> = {
  DRAFT: ['PENDING_APPROVAL', 'CANCELLED'],
  PENDING_APPROVAL: ['APPROVED', 'REJECTED', 'CANCELLED'],
  APPROVED: ['SCHEDULED', 'CANCELLED'],
  SCHEDULED: ['IN_PROGRESS', 'CANCELLED'],
  IN_PROGRESS: ['COMPLETED', 'FAILED', 'ROLLED_BACK', 'CANCELLED'],
  COMPLETED: ['CLOSED'],
  CLOSED: [],
  FAILED: ['ROLLED_BACK', 'CANCELLED'],
  ROLLED_BACK: [],
  REJECTED: ['CANCELLED'],
  CANCELLED: [],
}

function toLocalInput(iso: string | null) {
  return toEasternInputValue(iso)
}

const DATETIME_LOCAL_RE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/

function toIso(input: string) {
  if (!input || !DATETIME_LOCAL_RE.test(input)) return null
  return input + ':00Z'
}

export function ChangeDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const isNew = id === 'new'

  const [form, setForm] = useState<Record<string, string>>({
    title: '',
    description: '',
    changeType: 'NORMAL',
    risk: 'MEDIUM',
    plannedStart: '',
    plannedEnd: '',
    rollbackPlan: '',
    postImplementationReview: '',
    linkedProblemId: '',
    requestedById: '',
    locationId: '',
  })
  const [selectedStatus, setSelectedStatus] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  const [approvalOpen, setApprovalOpen] = useState(false)
  const [approvalStep, setApprovalStep] = useState<number | null>(null)
  const [approvalComment, setApprovalComment] = useState('')
  const [newApproverId, setNewApproverId] = useState('')
  const [newApproverOrder, setNewApproverOrder] = useState('1')
  const [confirmBack, setConfirmBack] = useState(false)

  const changeQuery = useQuery<Change>({
    queryKey: ['change', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/changes/${id}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !isNew && !!id,
  })

  const activityQuery = useQuery<AuditEntry[]>({
    queryKey: ['change-activity', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/changes/${id}/activity`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !isNew && !!id,
  })

  useDocumentTitle(
    isNew ? 'New Change' : changeQuery.data ? `Change #${changeQuery.data.number}` : 'Change Detail'
  )

  const usersQuery = useQuery<User[]>({
    queryKey: ['users'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/users')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const problemsQuery = useQuery<Problem[]>({
    queryKey: ['problems'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/problems')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const locationsQuery = useQuery<Location[]>({
    queryKey: ['locations'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/locations')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  useEffect(() => {
    if (changeQuery.data) {
      const c = changeQuery.data
      setForm({
        title: c.title ?? '',
        description: c.description ?? '',
        changeType: c.changeType ?? 'NORMAL',
        risk: c.risk ?? 'MEDIUM',
        plannedStart: toLocalInput(c.plannedStart),
        plannedEnd: toLocalInput(c.plannedEnd),
        rollbackPlan: c.rollbackPlan ?? '',
        postImplementationReview: c.postImplementationReview ?? '',
        linkedProblemId: c.linkedProblemId ?? '',
        requestedById: c.requestedById ?? '',
        locationId: c.locationId ?? '',
      })
    }
  }, [changeQuery.data])

  const saveMutation = useMutation<Change, Error, Record<string, string | null>>({
    mutationFn: async (payload) => {
      const url = isNew ? '/api/v1/changes' : `/api/v1/changes/${id}`
      const method = isNew ? 'POST' : 'PATCH'
      const res = await fetchWithToken(instance, account!, url, { method, body: JSON.stringify(payload) })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: (data) => {
      setActionError(null)
      queryClient.invalidateQueries({ queryKey: ['changes'] })
      if (isNew) {
        navigate(`/dashboard/changes/${data.id}`)
      } else {
        queryClient.invalidateQueries({ queryKey: ['change', id] })
        queryClient.invalidateQueries({ queryKey: ['change-activity', id] })
      }
    },
    onError: (error) => setActionError(error.message),
  })

  const statusMutation = useMutation<Change, Error, string>({
    mutationFn: async (status) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/changes/${id}/status?status=${status}`, { method: 'PATCH' })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      setSelectedStatus('')
      setActionError(null)
      queryClient.invalidateQueries({ queryKey: ['change', id] })
      queryClient.invalidateQueries({ queryKey: ['change-activity', id] })
      queryClient.invalidateQueries({ queryKey: ['changes'] })
    },
    onError: (error) => setActionError(error.message),
  })

  const submitForApproval = useMutation<Change, Error>({
    mutationFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/changes/${id}/submit-for-approval`, { method: 'POST' })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setActionError(null)
      queryClient.invalidateQueries({ queryKey: ['change', id] })
      queryClient.invalidateQueries({ queryKey: ['change-activity', id] })
      queryClient.invalidateQueries({ queryKey: ['changes'] })
    },
    onError: (error) => setActionError(error.message),
  })

  const approvalAction = useMutation<Change, Error, { approve: boolean; comment: string }>({
    mutationFn: async ({ approve, comment }) => {
      const action = approve ? 'approve' : 'reject'
      const res = await fetchWithToken(instance, account!, `/api/v1/changes/${id}/${action}?sequenceOrder=${approvalStep}&comment=${encodeURIComponent(comment)}`, { method: 'POST' })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      setApprovalOpen(false)
      setApprovalComment('')
      setApprovalStep(null)
      setActionError(null)
      queryClient.invalidateQueries({ queryKey: ['change', id] })
      queryClient.invalidateQueries({ queryKey: ['change-activity', id] })
      queryClient.invalidateQueries({ queryKey: ['changes'] })
    },
    onError: (error) => setActionError(error.message),
  })

  const addApprover = useMutation<Change, Error>({
    mutationFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/changes/${id}/approvals`, {
        method: 'POST',
        body: JSON.stringify({ approverId: newApproverId, sequenceOrder: Number(newApproverOrder) }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setNewApproverId('')
      setNewApproverOrder('1')
      setActionError(null)
      queryClient.invalidateQueries({ queryKey: ['change', id] })
      queryClient.invalidateQueries({ queryKey: ['change-activity', id] })
    },
    onError: (error) => setActionError(error.message),
  })

  const activities = useMemo<Activity[]>(() => {
    if (!changeQuery.data) return []
    const change = changeQuery.data
    const list: Activity[] = []
    list.push({
      id: `${change.id}-created`,
      title: 'Change request created',
      description: `Change #${change.number} opened by ${change.requestedByName || 'Unknown'}`,
      actorName: change.requestedByName || undefined,
      createdAt: change.createdAt,
      icon: <History className="h-4 w-4" />,
    })
    list.push({
      id: `${change.id}-status`,
      title: `Status updated to ${change.status}`,
      createdAt: change.createdAt,
      icon: <MessageCircle className="h-4 w-4" />,
    })
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
  }, [changeQuery.data, activityQuery.data])

  const initialForm = useMemo(() => {
    if (isNew) {
      return {
        title: '',
        description: '',
        changeType: 'NORMAL',
        risk: 'MEDIUM',
        plannedStart: '',
        plannedEnd: '',
        rollbackPlan: '',
        postImplementationReview: '',
        linkedProblemId: '',
        requestedById: '',
        locationId: '',
      } as Record<string, string>
    }
    const c = changeQuery.data
    return (c
      ? {
          title: c.title ?? '',
          description: c.description ?? '',
          changeType: c.changeType ?? 'NORMAL',
          risk: c.risk ?? 'MEDIUM',
          plannedStart: toLocalInput(c.plannedStart),
          plannedEnd: toLocalInput(c.plannedEnd),
          rollbackPlan: c.rollbackPlan ?? '',
          postImplementationReview: c.postImplementationReview ?? '',
          linkedProblemId: c.linkedProblemId ?? '',
          requestedById: c.requestedById ?? '',
          locationId: c.locationId ?? '',
        }
      : {}) as Record<string, string>
  }, [changeQuery.data, isNew])

  const formDirty = useMemo(() => {
    const baseDirty = Object.keys(initialForm).some((key) => form[key] !== initialForm[key])
    return baseDirty || selectedStatus !== ''
  }, [form, initialForm, selectedStatus])

  const handleBack = () => {
    if (formDirty) {
      setConfirmBack(true)
    } else {
      navigate('/dashboard/changes')
    }
  }

  if (!isNew && (changeQuery.isLoading || !changeQuery.data)) return <Loading />
  if (!isNew && changeQuery.error) return <ErrorFallback error={changeQuery.error} message="Could not load change." onRetry={() => changeQuery.refetch()} />

  const change = changeQuery.data
  const currentStatus = change?.status ?? 'DRAFT'
  const legalNextStatuses = statusTransitions[currentStatus] ?? []
  const pirsMissing = !form.postImplementationReview.trim()

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault()
    const payload: Record<string, string | null> = {
      title: form.title,
      description: form.description,
      changeType: form.changeType,
      risk: form.risk,
      plannedStart: toIso(form.plannedStart),
      plannedEnd: toIso(form.plannedEnd),
      rollbackPlan: form.rollbackPlan,
      linkedProblemId: form.linkedProblemId || null,
      locationId: form.locationId || null,
    }
    if (!isNew) {
      payload.postImplementationReview = form.postImplementationReview
    }
    if (isNew) {
      payload.requestedById = form.requestedById || null
    }
    saveMutation.mutate(payload)
  }

  const handleStatusChange = () => {
    if (!selectedStatus) return
    if (selectedStatus === 'CLOSED' && pirsMissing) {
      setActionError('A Post-Implementation Review is required before a change can be closed.')
      return
    }
    statusMutation.mutate(selectedStatus)
  }

  const sortedApprovals = (change?.approvals ?? []).slice().sort((a, b) => a.sequenceOrder - b.sequenceOrder)
  const nextPendingIndex = (() => {
    for (let i = 0; i < sortedApprovals.length; i++) {
      if (sortedApprovals[i].status === 'PENDING') {
        const previousApproved = sortedApprovals.slice(0, i).every((a) => a.status === 'APPROVED')
        if (previousApproved) return i
      }
    }
    return -1
  })()

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-5xl space-y-6">
        <div className="flex items-center gap-4">
          <button
            type="button"
            onClick={handleBack}
            className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </button>
          <h1 className="text-2xl font-semibold tracking-tight">
            {isNew ? 'New Change Request' : `Change #${change?.number}`}
          </h1>
        </div>

        {!isNew && change && (
          <div className="flex flex-wrap items-center gap-3 text-sm">
            <StatusBadge status={change.status} />
            <span className="text-muted-foreground">{change.changeType}</span>
            <span className="text-muted-foreground">·</span>
            <span className="text-muted-foreground">{change.risk} risk</span>
            <span className="text-muted-foreground">·</span>
            <span className="text-muted-foreground">{change.requestedByName}</span>
          </div>
        )}

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold">Details</h2>
          <EntityForm
            fields={[
              { name: 'title', label: 'Title', type: 'text', required: true },
              { name: 'description', label: 'Description', type: 'textarea' },
              { name: 'changeType', label: 'Change Type', type: 'select', options: ['STANDARD', 'NORMAL', 'EMERGENCY'].map((v) => ({ value: v, label: v })), required: true },
              { name: 'risk', label: 'Risk', type: 'select', options: ['LOW', 'MEDIUM', 'HIGH'].map((v) => ({ value: v, label: v })), required: true },
              { name: 'plannedStart', label: 'Planned Start', type: 'datetime-local' as const },
              { name: 'plannedEnd', label: 'Planned End', type: 'datetime-local' as const },
              { name: 'rollbackPlan', label: 'Rollback Plan', type: 'textarea' },
              { name: 'postImplementationReview', label: 'Post-Implementation Review', type: 'textarea' as const },
              { name: 'linkedProblemId', label: 'Linked Problem', type: 'select' as const, options: (problemsQuery.data ?? []).map((p) => ({ value: p.id, label: `${p.number} — ${p.title}` })) },
              { name: 'locationId', label: 'Affected Location', type: 'select' as const, options: (locationsQuery.data ?? []).map((l) => ({ value: l.id, label: l.name })) },
              ...(isNew ? [{ name: 'requestedById', label: 'Requested By', type: 'select' as const, options: (usersQuery.data ?? []).map((u) => ({ value: u.id, label: u.displayName })) }] : []),
            ]}
            values={form}
            onChange={(name, value) => setForm({ ...form, [name]: value })}
            onSubmit={handleSave}
            submitLabel={isNew ? 'Create' : 'Save'}
            pending={saveMutation.isPending}
          />
          {actionError && <p className="mt-4 text-sm text-destructive">{actionError}</p>}
        </section>

        {!isNew && change && (
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
                    <option key={s} value={s} disabled={s === 'CLOSED' && pirsMissing}>{s}</option>
                  ))}
                </select>
              </div>
              <button
                onClick={handleStatusChange}
                disabled={!selectedStatus || statusMutation.isPending}
                className="inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
              >
                Transition
              </button>
            </div>
            {currentStatus === 'DRAFT' && (
              <button
                onClick={() => submitForApproval.mutate()}
                disabled={submitForApproval.isPending}
                className="mt-4 inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
              >
                Submit for Approval
              </button>
            )}
          </section>
        )}

        {!isNew && change && (
          <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
            <h2 className="mb-4 text-lg font-semibold">Approval</h2>
            {change.changeType === 'STANDARD' && (
              <div className="inline-flex items-center gap-2 rounded-md bg-green-100 px-3 py-1 text-sm font-medium text-green-800">
                <CheckCircle2 className="h-4 w-4" />
                Auto-approved (Standard)
              </div>
            )}

            {change.changeType === 'EMERGENCY' && (
              <p className="text-sm text-muted-foreground">Expedited workflow — any single approval moves the change to IN_PROGRESS.</p>
            )}

            {change.changeType === 'NORMAL' && (
              <div className="space-y-3">
                {sortedApprovals.length === 0 && <p className="text-sm text-muted-foreground">No approvers defined.</p>}
                {sortedApprovals.map((a, idx) => {
                  const isCurrent = idx === nextPendingIndex
                  return (
                    <div key={a.id} className={`flex items-center justify-between rounded-md border p-3 ${isCurrent ? 'border-primary bg-primary/5' : 'border-border'}`}>
                      <div className="text-sm">
                        <p className="font-medium">{a.sequenceOrder}. {a.approverName}</p>
                        {a.comment && <p className="text-muted-foreground">{a.comment}</p>}
                        {a.decidedAt && <p className="text-xs text-muted-foreground">{formatDateTime(a.decidedAt)}</p>}
                      </div>
                      <div className="flex items-center gap-2">
                        <StatusBadge status={a.status} />
                        {a.status === 'PENDING' && isCurrent && (
                          <button
                            onClick={() => { setApprovalStep(a.sequenceOrder); setApprovalOpen(true) }}
                            className="rounded-md border border-border px-2 py-1 text-xs font-medium transition hover:bg-muted"
                          >
                            Act
                          </button>
                        )}
                      </div>
                    </div>
                  )
                })}

                {['PENDING_APPROVAL', 'DRAFT'].includes(currentStatus) && (
                  <div className="flex items-end gap-2">
                    <input
                      value={newApproverId}
                      onChange={(e) => setNewApproverId(e.target.value)}
                      placeholder="Approver ID"
                      className="w-48 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                    />
                    <input
                      type="number"
                      value={newApproverOrder}
                      onChange={(e) => setNewApproverOrder(e.target.value)}
                      className="w-20 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                    />
                    <button
                      onClick={() => addApprover.mutate()}
                      disabled={!newApproverId}
                      className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
                    >
                      Add
                    </button>
                  </div>
                )}
              </div>
            )}
          </section>
        )}

        {pirsMissing && change?.status === 'COMPLETED' && (
          <div className="rounded-md border border-yellow-500/50 bg-yellow-500/10 p-4 text-sm text-yellow-900">
            <p className="font-semibold">Post-Implementation Review required</p>
            <p>Marking this change CLOSED is blocked until the Post-Implementation Review field is filled.</p>
          </div>
        )}

        {!isNew && <ActivityTimeline activities={activities} />}
      </div>

      <FormDrawer open={approvalOpen} title="Approve / Reject" dirty={approvalComment.trim() !== ''} onClose={() => { setApprovalOpen(false); setApprovalComment(''); setApprovalStep(null) }}>
        <div className="space-y-4">
          <label className="text-sm font-medium">Approver step {approvalStep}</label>
          <textarea
            value={approvalComment}
            onChange={(e) => setApprovalComment(e.target.value)}
            placeholder="Comment (optional)"
            rows={3}
            className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
          />
          <div className="flex gap-2">
            <button
              onClick={() => approvalAction.mutate({ approve: true, comment: approvalComment })}
              disabled={approvalAction.isPending}
              className="inline-flex flex-1 items-center justify-center gap-1 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
            >
              <Check className="h-4 w-4" />
              Approve
            </button>
            <button
              onClick={() => approvalAction.mutate({ approve: false, comment: approvalComment })}
              disabled={approvalAction.isPending}
              className="inline-flex flex-1 items-center justify-center gap-1 rounded-md bg-destructive px-4 py-2 text-sm font-medium text-destructive-foreground transition hover:bg-destructive/90 disabled:opacity-50"
            >
              <XCircle className="h-4 w-4" />
              Reject
            </button>
          </div>
        </div>
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
          navigate('/dashboard/changes')
        }}
        onCancel={() => setConfirmBack(false)}
      />
    </div>
  )
}
