import { useEffect, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Check, CheckCircle2, XCircle } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { EntityForm } from '../../components/ui/EntityForm'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { FormDrawer } from '../../components/ui/FormDrawer'
import { Loading } from '../../components/ui/Loading'
import { StatusBadge } from '../../components/ui/StatusBadge'

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
  requestedByName: string
  plannedStart: string | null
  plannedEnd: string | null
  rollbackPlan: string
  postImplementationReview: string
  linkedProblemId: string | null
  approvals: ChangeApproval[]
}

const statusTransitions: Record<string, string[]> = {
  DRAFT: ['PENDING_APPROVAL', 'CANCELLED'],
  PENDING_APPROVAL: ['APPROVED', 'REJECTED', 'CANCELLED'],
  APPROVED: ['SCHEDULED', 'CANCELLED'],
  SCHEDULED: ['IN_PROGRESS', 'CANCELLED'],
  IN_PROGRESS: ['COMPLETED', 'FAILED', 'ROLLED_BACK', 'CANCELLED'],
  COMPLETED: [],
  FAILED: ['ROLLED_BACK', 'CANCELLED'],
  ROLLED_BACK: [],
  REJECTED: ['CANCELLED'],
  CANCELLED: [],
}

function toLocalInput(iso: string | null) {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function toIso(input: string) {
  if (!input) return null
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
  })
  const [selectedStatus, setSelectedStatus] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  const [approvalOpen, setApprovalOpen] = useState(false)
  const [approvalStep, setApprovalStep] = useState<number | null>(null)
  const [approvalComment, setApprovalComment] = useState('')
  const [newApproverId, setNewApproverId] = useState('')
  const [newApproverOrder, setNewApproverOrder] = useState('1')

  const changeQuery = useQuery<Change>({
    queryKey: ['change', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/changes/${id}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !isNew && !!id,
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
        requestedById: '',
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
    },
    onError: (error) => setActionError(error.message),
  })

  if (!isNew && (changeQuery.isLoading || !changeQuery.data)) return <Loading />
  if (!isNew && changeQuery.error) return <ErrorFallback error={changeQuery.error} message="Could not load change." onRetry={() => changeQuery.refetch()} />

  const change = changeQuery.data
  const currentStatus = change?.status ?? 'DRAFT'
  const currentType = form.changeType
  const legalNextStatuses = statusTransitions[currentStatus] ?? []
  const pirsMissing = currentType === 'EMERGENCY' && !form.postImplementationReview.trim()

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
    if (selectedStatus === 'COMPLETED' && pirsMissing) {
      setActionError('A Post-Implementation Review is required before an EMERGENCY change can be marked COMPLETED.')
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
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-5xl space-y-6">
        <div className="flex items-center gap-4">
          <Link to="/dashboard/changes" className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted">
            <ArrowLeft className="h-4 w-4" />
            Back
          </Link>
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
              { name: 'plannedStart', label: 'Planned Start', type: 'text' },
              { name: 'plannedEnd', label: 'Planned End', type: 'text' },
              { name: 'rollbackPlan', label: 'Rollback Plan', type: 'textarea' },
              ...(currentType === 'EMERGENCY' ? [{ name: 'postImplementationReview', label: 'Post-Implementation Review', type: 'textarea' as const }] : []),
              { name: 'linkedProblemId', label: 'Linked Problem ID', type: 'text' },
              ...(isNew ? [{ name: 'requestedById', label: 'Requested By ID', type: 'text' as const }] : []),
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
                    <option key={s} value={s} disabled={s === 'COMPLETED' && pirsMissing}>{s}</option>
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
                        {a.decidedAt && <p className="text-xs text-muted-foreground">{new Date(a.decidedAt).toLocaleString()}</p>}
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

        {pirsMissing && (
          <div className="rounded-md border border-yellow-500/50 bg-yellow-500/10 p-4 text-sm text-yellow-900">
            <p className="font-semibold">EMERGENCY change — Post-Implementation Review required</p>
            <p>Marking this change COMPLETED is blocked until the Post-Implementation Review field is filled.</p>
          </div>
        )}
      </div>

      <FormDrawer open={approvalOpen} title="Approve / Reject" onClose={() => { setApprovalOpen(false); setApprovalComment(''); setApprovalStep(null) }}>
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
    </div>
  )
}
