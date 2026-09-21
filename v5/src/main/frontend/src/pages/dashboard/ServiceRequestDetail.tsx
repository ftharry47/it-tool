import { useEffect, useMemo, useState } from 'react'
import { useParams, useNavigate, useLocation } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, CheckCircle2 } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { useDocumentTitle } from '../../components/layout/useDocumentTitle'
import { CommentThread } from '../../components/ui/CommentThread'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { CopyButton } from '../../components/ui/CopyButton'
import { DataTable } from '../../components/ui/DataTable'
import { DateInput } from '../../components/ui/DateInput'
import { EntityForm } from '../../components/ui/EntityForm'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { FormDrawer } from '../../components/ui/FormDrawer'
import { Loading } from '../../components/ui/Loading'
import { SchemaForm, isValidSchema } from '../../components/ui/SchemaForm'
import { StatusBadge, formatStatusLabel } from '../../components/ui/StatusBadge'
import { formatDateTime } from '../../lib/date'

interface FulfillmentTask {
  id: string
  description: string
  sequenceOrder: number
  status: string
  workflow?: 'INSTANT' | 'SOFTWARE' | 'FULL'
  assigneeId: string | null
  assigneeName: string | null
  orderId?: string | null
  vendor?: string | null
  expectedDeliveryDate: string | null
  deliveredAt: string | null
}

interface TeamInfo {
  id: string
  name: string
  members: { userId: string; displayName: string; email: string }[]
}

interface ActivityEntry {
  id: string
  action: string
  actorUserId: string | null
  actorName: string | null
  beforeState: string | null
  afterState: string | null
  createdAt: string
}

interface PendingTaskAction {
  type: 'assign' | 'ordered' | 'installed' | 'complete'
  taskId: string
  workflow: 'FULL' | 'SOFTWARE' | 'INSTANT'
  description: string
  assigneeId?: string
  reassign?: boolean
}

const ACTION_LABELS: Record<string, string> = {
  SUBMITTED: 'Submitted',
  ROUTED_TO_APPROVER: 'Routed to approver',
  SENT_TO_APPROVAL: 'Sent to approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  IN_FULFILLMENT: 'Moved to fulfillment',
  FULFILLED: 'Fulfilled',
  TASK_ASSIGNED: 'Task assigned',
  TASK_ORDERED: 'Marked ordered',
  DELIVERY_DATE_SET: 'Delivery date set',
  DELIVERED: 'Marked installed',
  TASK_COMPLETED: 'Task completed',
  SEND_REMINDER: 'Reminder sent',
  EDITED: 'Request edited by admin',
  APPROVAL_BYPASSED: 'Approval bypassed by admin',
}

function actionLabel(action: string): string {
  return ACTION_LABELS[action] ?? formatStatusLabel(action)
}

function describeAfter(afterState: string | null): string | null {
  if (!afterState) return null
  try {
    const s = JSON.parse(afterState)
    if (s.reason) return `Reason: ${s.reason}`
    if (s.approverName) return `Approver: ${s.approverName}`
    if (s.assigneeName) return `Assigned to: ${s.assigneeName}`
    if (s.expectedDeliveryDate) return `Expected delivery: ${s.expectedDeliveryDate}`
    if (s.taskDescription) return `Task: ${s.taskDescription}`
    if (s.comment) return s.comment
    return null
  } catch {
    return null
  }
}

interface ServiceRequestDetail {
  id: string
  number: number
  catalogItemName: string
  requesterName: string
  status: string
  formData: string
  approvalRequired: boolean
  approverId: string | null
  approverName: string | null
  approvalDecision: string
  approvalComment: string | null
  approvalBypassed: boolean
  bypassedById: string | null
  bypassedByName: string | null
  decidedAt: string | null
  neededBy: string | null
  catalogItemId: string
  locationId: string | null
  locationName: string | null
  phone: string | null
  createdAt: string
  priorityId: string | null
  priorityName: string | null
  tasks: FulfillmentTask[]
}

export function ServiceRequestDetail() {
  const { id } = useParams<{ id: string }>()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const { currentUser } = useAuth()
  const canPostInternal = (currentUser?.roles ?? []).some((r) =>
    ['AGENT', 'TEAM_LEAD', 'ADMIN', 'SUPER_ADMIN'].includes(r)
  )
  const isSuperAdmin = (currentUser?.roles ?? []).includes('SUPER_ADMIN')
  const isAdminOrSuperAdmin = (currentUser?.roles ?? []).some((r) =>
    ['ADMIN', 'SUPER_ADMIN'].includes(r)
  )

  const [approveOpen, setApproveOpen] = useState(false)
  const [rejectOpen, setRejectOpen] = useState(false)
  const [approveForm, setApproveForm] = useState({ comment: '' })
  const [rejectForm, setRejectForm] = useState({ comment: '' })
  const [sendToApprovalReason, setSendToApprovalReason] = useState('')
  const [sendToApprovalOpen, setSendToApprovalOpen] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [pendingTaskAction, setPendingTaskAction] = useState<PendingTaskAction | null>(null)

  const requestQuery = useQuery<ServiceRequestDetail>({
    queryKey: ['service-request', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!id,
  })

  useDocumentTitle(
    requestQuery.data ? `Request #${requestQuery.data.number}` : 'Request Detail'
  )

  const activityQuery = useQuery<ActivityEntry[]>({
    queryKey: ['service-request-activity', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}/activity`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!id,
  })

  const approveMutation = useMutation<ServiceRequestDetail, Error, string>({
    mutationFn: async (comment) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}/approve`, {
        method: 'POST',
        body: JSON.stringify({ comment, approve: true }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setApproveOpen(false)
      setApproveForm({ comment: '' })
      setActionError(null)
      queryClient.invalidateQueries({ queryKey: ['service-request', id] })
      queryClient.invalidateQueries({ queryKey: ['service-request-activity', id] })
      queryClient.invalidateQueries({ queryKey: ['service-requests'] })
    },
    onError: (error) => setActionError(error.message),
  })

  const rejectMutation = useMutation<ServiceRequestDetail, Error, string>({
    mutationFn: async (comment) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}/reject`, {
        method: 'POST',
        body: JSON.stringify({ comment, approve: false }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setRejectOpen(false)
      setRejectForm({ comment: '' })
      setActionError(null)
      queryClient.invalidateQueries({ queryKey: ['service-request', id] })
      queryClient.invalidateQueries({ queryKey: ['service-request-activity', id] })
      queryClient.invalidateQueries({ queryKey: ['service-requests'] })
    },
    onError: (error) => setActionError(error.message),
  })

  const sendReminderMutation = useMutation<ServiceRequestDetail, Error, string>({
    mutationFn: async (message) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}/send-reminder`, {
        method: 'POST',
        body: JSON.stringify({ message }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setActionError(null)
      queryClient.invalidateQueries({ queryKey: ['service-request', id] })
      queryClient.invalidateQueries({ queryKey: ['service-request-activity', id] })
      queryClient.invalidateQueries({ queryKey: ['service-requests'] })
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
    onError: (error) => setActionError(error.message),
  })

  const sendToApprovalMutation = useMutation<ServiceRequestDetail, Error, string>({
    mutationFn: async (reason) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}/send-to-approval`, {
        method: 'POST',
        body: JSON.stringify({ reason }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setSendToApprovalOpen(false)
      setSendToApprovalReason('')
      setActionError(null)
      queryClient.invalidateQueries({ queryKey: ['service-request', id] })
      queryClient.invalidateQueries({ queryKey: ['service-request-activity', id] })
      queryClient.invalidateQueries({ queryKey: ['service-requests'] })
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
    onError: (error) => setActionError(error.message),
  })

  const defaultReminder = useMemo(() => {
    const req = requestQuery.data
    if (!req) return ''
    return `Request #${req.number} (${req.catalogItemName}) is awaiting your approval. Please review and approve or reject.`
  }, [requestQuery.data])

  const [reminderMessage, setReminderMessage] = useState('')
  useEffect(() => {
    setReminderMessage(defaultReminder)
  }, [defaultReminder])

  // IT Fulfillment team members are the only valid task assignees.
  const teamsQuery = useQuery<TeamInfo[]>({
    queryKey: ['teams'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/teams')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && isSuperAdmin,
  })
  const fulfillmentMembers =
    teamsQuery.data?.find((t) => t.name === 'IT Fulfillment')?.members ?? []

  const assignMutation = useMutation<ServiceRequestDetail, Error, { taskId: string; assigneeId: string }>({
    mutationFn: async ({ taskId, assigneeId }) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}/tasks/${taskId}/assign`, {
        method: 'POST',
        body: JSON.stringify({ assigneeId }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['service-request', id] })
      queryClient.invalidateQueries({ queryKey: ['service-request-activity', id] })
    },
    onError: (error) => setActionError(error.message),
  })

  const [deliveryDateTask, setDeliveryDateTask] = useState<string | null>(null)
  const [deliveryDate, setDeliveryDate] = useState('')
  const [orderTask, setOrderTask] = useState<string | null>(null)
  const [orderForm, setOrderForm] = useState({ orderId: '', vendor: '' })
  const [completeTask, setCompleteTask] = useState<string | null>(null)
  const [closingNotes, setClosingNotes] = useState('')

  const invalidateTaskQueries = () => {
    queryClient.invalidateQueries({ queryKey: ['service-request', id] })
    queryClient.invalidateQueries({ queryKey: ['service-request-activity', id] })
    queryClient.invalidateQueries({ queryKey: ['service-requests'] })
  }

  const orderedMutation = useMutation<ServiceRequestDetail, Error, { taskId: string; orderId?: string; vendor?: string }>({
    mutationFn: async ({ taskId, orderId, vendor }) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}/tasks/${taskId}/ordered`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderId, vendor }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setOrderTask(null)
      setOrderForm({ orderId: '', vendor: '' })
      invalidateTaskQueries()
    },
    onError: (error) => setActionError(error.message),
  })

  const deliveryDateMutation = useMutation<ServiceRequestDetail, Error, { taskId: string; date: string }>({
    mutationFn: async ({ taskId, date }) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}/tasks/${taskId}/delivery-date`, {
        method: 'PATCH',
        body: JSON.stringify({ expectedDeliveryDate: date }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setDeliveryDateTask(null)
      setDeliveryDate('')
      invalidateTaskQueries()
    },
    onError: (error) => setActionError(error.message),
  })

  const deliverMutation = useMutation<ServiceRequestDetail, Error, string>({
    mutationFn: async (taskId) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}/tasks/${taskId}/deliver`, {
        method: 'POST',
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: invalidateTaskQueries,
    onError: (error) => setActionError(error.message),
  })

  const taskMutation = useMutation<ServiceRequestDetail, Error, { taskId: string; closingNotes: string }>({
    mutationFn: async ({ taskId, closingNotes }) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}/tasks/${taskId}/complete`, {
        method: 'POST',
        body: JSON.stringify({ closingNotes }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setCompleteTask(null)
      setClosingNotes('')
      invalidateTaskQueries()
    },
    onError: (error) => setActionError(error.message),
  })

  // Hold/resume flips the request's status; the backend pauses/resumes its SLA.
  const statusMutation = useMutation<ServiceRequestDetail, Error, 'ON_HOLD' | 'IN_FULFILLMENT'>({
    mutationFn: async (status) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}/status?status=${status}`, {
        method: 'PATCH',
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: invalidateTaskQueries,
    onError: (error) => setActionError(error.message),
  })

  // ---- SUPER_ADMIN: record edit + approval bypass ----

  const [editOpen, setEditOpen] = useState(false)
  const [editForm, setEditForm] = useState({
    locationId: '',
    priorityId: '',
    phone: '',
    neededBy: '',
  })
  const [editFormData, setEditFormData] = useState<Record<string, string>>({})
  const [bypassOpen, setBypassOpen] = useState(false)
  const [bypassReason, setBypassReason] = useState('')

  const locationsQuery = useQuery<{ id: string; name: string }[]>({
    queryKey: ['locations'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/locations')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && isSuperAdmin,
  })

  const prioritiesQuery = useQuery<{ id: string; name: string }[]>({
    queryKey: ['priorities'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/incidents/priorities')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && isSuperAdmin,
  })

  // The request response doesn't carry the item's schema — fetch it for the
  // edit drawer so the dynamic form fields render exactly like submission.
  const catalogItemQuery = useQuery<{ formSchema: string } | null>({
    queryKey: ['catalog-item-schema', requestQuery.data?.catalogItemId],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/catalog-items')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const items: { id: string; formSchema: string }[] = await res.json()
      return items.find((i) => i.id === requestQuery.data?.catalogItemId) ?? null
    },
    enabled: !!account && isSuperAdmin && !!requestQuery.data?.catalogItemId,
  })

  const updateMutation = useMutation<ServiceRequestDetail, Error, void>({
    mutationFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          locationId: editForm.locationId || null,
          priorityId: editForm.priorityId || null,
          phone: editForm.phone || null,
          neededBy: editForm.neededBy ? new Date(editForm.neededBy).toISOString() : null,
          formData: JSON.stringify(editFormData),
        }),
      })
      if (!res.ok) throw new Error((await res.text()) || `HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setEditOpen(false)
      setActionError(null)
      invalidateTaskQueries()
    },
    onError: (error) => setActionError(error.message),
  })

  const bypassMutation = useMutation<ServiceRequestDetail, Error, string>({
    mutationFn: async (reason) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}/bypass-approval`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason }),
      })
      if (!res.ok) throw new Error((await res.text()) || `HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      setBypassOpen(false)
      setBypassReason('')
      setActionError(null)
      invalidateTaskQueries()
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
    onError: (error) => setActionError(error.message),
  })

  const openEditDrawer = () => {
    const r = requestQuery.data
    if (!r) return
    let parsed: Record<string, string> = {}
    try {
      parsed = JSON.parse(r.formData)
    } catch { /* malformed data — start empty */ }
    setEditForm({
      locationId: r.locationId ?? '',
      priorityId: r.priorityId ?? '',
      phone: r.phone ?? '',
      neededBy: r.neededBy ? r.neededBy.slice(0, 10) : '',
    })
    setEditFormData(parsed)
    setActionError(null)
    setEditOpen(true)
  }

  if (requestQuery.isLoading) return <Loading />
  if (requestQuery.error) return <ErrorFallback error={requestQuery.error} message="Could not load request." onRetry={() => requestQuery.refetch()} />
  if (!requestQuery.data) return <Loading />

  const request = requestQuery.data
  const formDataDisplay = (() => {
    try {
      return JSON.parse(request.formData)
    } catch {
      return {}
    }
  })()

  const requestUrl = `${window.location.origin}/dashboard/service-requests/${request.id}`
  const requestCopyPlain = `Request #${request.number}: ${request.catalogItemName}\nStatus: ${formatStatusLabel(request.status)}\nApproval: ${formatStatusLabel(request.approvalDecision)}\nRequester: ${request.requesterName}\nLocation: ${request.locationName ?? '—'}\nSubmitted: ${formatDateTime(request.createdAt)}\n\nView: ${requestUrl}`
  const requestCopyHtml = `<b>Request #${request.number}:</b> ${request.catalogItemName}<br><b>Status:</b> ${formatStatusLabel(request.status)}<br><b>Approval:</b> ${formatStatusLabel(request.approvalDecision)}<br><b>Requester:</b> ${request.requesterName}<br><b>Location:</b> ${request.locationName ?? '—'}<br><b>Submitted:</b> ${formatDateTime(request.createdAt)}<br><br><b>View:</b> <a href="${requestUrl}">Open request</a>`

  const confirmTaskTitle = (action: PendingTaskAction) => {
    switch (action.type) {
      case 'assign': return action.reassign ? 'Reassign task?' : 'Assign task?'
      case 'ordered': return action.workflow === 'SOFTWARE' ? 'Mark provisioned?' : 'Mark ordered?'
      case 'installed': return action.workflow === 'SOFTWARE' ? 'Mark granted?' : 'Mark installed?'
      case 'complete': return 'Complete task?'
    }
  }

  const confirmTaskLabel = (action: PendingTaskAction) => {
    switch (action.type) {
      case 'assign': return action.reassign ? 'Reassign' : 'Assign'
      case 'ordered': return action.workflow === 'SOFTWARE' ? 'Mark Provisioned' : 'Mark Ordered'
      case 'installed': return action.workflow === 'SOFTWARE' ? 'Mark Granted' : 'Mark Installed'
      case 'complete': return 'Complete'
    }
  }

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <ConfirmDialog
        open={pendingTaskAction !== null}
        title={pendingTaskAction ? confirmTaskTitle(pendingTaskAction) : ''}
        description={pendingTaskAction?.description}
        confirmLabel={pendingTaskAction ? confirmTaskLabel(pendingTaskAction) : 'Confirm'}
        destructive={pendingTaskAction?.type === 'complete'}
        pending={pendingTaskAction ?
          (pendingTaskAction.type === 'assign' ? assignMutation.isPending :
            pendingTaskAction.type === 'ordered' ? orderedMutation.isPending :
              pendingTaskAction.type === 'installed' ? deliverMutation.isPending : false)
          : false}
        onConfirm={() => {
          if (pendingTaskAction) {
            const { type, taskId, assigneeId } = pendingTaskAction
            if (type === 'assign' && assigneeId) assignMutation.mutate({ taskId, assigneeId })
            if (type === 'ordered') orderedMutation.mutate({ taskId })
            if (type === 'installed') deliverMutation.mutate(taskId)
            if (type === 'complete') {
              setCompleteTask(taskId)
              setClosingNotes('')
              setActionError(null)
            }
          }
          setPendingTaskAction(null)
        }}
        onCancel={() => setPendingTaskAction(null)}
      />
      <div className="mx-auto max-w-5xl space-y-6">
        <div className="flex flex-wrap items-center gap-4">
          <button
            onClick={() => navigate(pathname.startsWith('/home') ? '/home/approvals' : '/dashboard/service-requests')}
            className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </button>
          <h1 className="text-2xl font-semibold tracking-tight">
            Request #{request.number} — {request.catalogItemName}
          </h1>
          <div className="ml-auto">
            <CopyButton html={requestCopyHtml} plain={requestCopyPlain} />
          </div>
        </div>

        <div className="grid gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">
            <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="text-lg font-semibold">Details</h2>
                {isSuperAdmin && (
                  <button
                    onClick={openEditDrawer}
                    className="rounded-md border border-border px-3 py-1.5 text-xs font-medium text-muted-foreground transition hover:bg-muted"
                  >
                    Edit (admin)
                  </button>
                )}
              </div>
              <dl className="grid gap-2 text-sm sm:grid-cols-2">
                <div className="flex justify-between sm:block">
                  <dt className="text-muted-foreground">Requester</dt>
                  <dd className="font-medium">{request.requesterName}</dd>
                </div>
                <div className="flex justify-between sm:block">
                  <dt className="text-muted-foreground">Status</dt>
                  <dd><StatusBadge status={request.status} /></dd>
                </div>
                <div className="flex justify-between sm:block">
                  <dt className="text-muted-foreground">Approval</dt>
                  <dd><StatusBadge status={request.approvalDecision} /></dd>
                </div>
                <div className="flex justify-between sm:block">
                  <dt className="text-muted-foreground">{request.approvalBypassed ? 'Designated approver' : 'Approver'}</dt>
                  <dd className="font-medium">{request.approverName ?? '—'}</dd>
                </div>
                {request.approvalBypassed && (
                  <div className="flex justify-between sm:block">
                    <dt className="text-muted-foreground">Bypassed by</dt>
                    <dd className="font-medium text-amber-600 dark:text-amber-400">{request.bypassedByName ?? '—'}</dd>
                  </div>
                )}
                <div className="flex justify-between sm:block">
                  <dt className="text-muted-foreground">Location</dt>
                  <dd className="font-medium">{request.locationName ?? '—'}</dd>
                </div>
                <div className="flex justify-between sm:block">
                  <dt className="text-muted-foreground">Priority</dt>
                  <dd className="font-medium">{request.priorityName ?? '—'}</dd>
                </div>
                {request.approvalComment && (
                  <div className="sm:col-span-2">
                    <dt className="text-muted-foreground">Approval comment</dt>
                    <dd className="mt-1 rounded-md bg-muted p-2">{request.approvalComment}</dd>
                  </div>
                )}
              </dl>

              <h3 className="mb-2 mt-6 text-sm font-semibold">Submitted Data</h3>
              <ul className="space-y-1 text-sm">
                {Object.entries(formDataDisplay).map(([key, value]) => (
                  <li key={key}>
                    <span className="text-muted-foreground">{key}:</span> <span className="font-medium">{String(value)}</span>
                  </li>
                ))}
              </ul>
            </section>

            <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
              <h2 className="mb-4 text-lg font-semibold">Fulfillment Tasks</h2>
              <DataTable<FulfillmentTask>
                caption="Tasks for this request"
                columns={[
                  { key: 'sequenceOrder', header: '#' },
                  { key: 'description', header: 'Task' },
                  { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
                  {
                    key: 'assigneeName',
                    header: 'Assignee',
                    render: (row) =>
                      isSuperAdmin ? (
                        <select
                          value={row.assigneeId ?? ''}
                          onChange={(e) => {
                            const assigneeId = e.target.value
                            if (assigneeId) {
                              const member = fulfillmentMembers.find((m) => m.userId === assigneeId)
                              setPendingTaskAction({
                                type: 'assign',
                                taskId: row.id,
                                workflow: (row.workflow ?? 'FULL') as 'FULL' | 'SOFTWARE' | 'INSTANT',
                                description: `${row.assigneeId ? 'Reassign' : 'Assign'} task "${row.description}" to ${member?.displayName ?? 'selected fulfiller'}?`,
                                assigneeId,
                                reassign: !!row.assigneeId,
                              })
                            }
                          }}
                          disabled={assignMutation.isPending}
                          aria-label={`Assign fulfiller for ${row.description}`}
                          className="rounded-md border border-input bg-background px-2 py-1 text-xs outline-none focus:ring-2 focus:ring-ring"
                        >
                          <option value="">{row.assigneeName ? `${row.assigneeName} — reassign…` : 'Assign…'}</option>
                          {fulfillmentMembers.map((m) => (
                            <option key={m.userId} value={m.userId}>{m.displayName}</option>
                          ))}
                        </select>
                      ) : (
                        <span className="text-xs">{row.assigneeName ?? '—'}</span>
                      ),
                  },
                  {
                    key: 'actions',
                    header: 'Actions',
                    render: (row) => {
                      const workflow = row.workflow ?? 'FULL'
                      const btn = 'inline-flex items-center gap-1 rounded-md bg-primary px-2 py-1 text-xs font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50'
                      if (row.status === 'COMPLETED') {
                        return <span className="text-xs text-muted-foreground">Done</span>
                      }
                      if (row.status === 'PENDING') {
                        if (workflow === 'INSTANT') {
                          return (
                            <button onClick={() => { setCompleteTask(row.id); setClosingNotes(''); setActionError(null) }} className={btn}>
                              <CheckCircle2 className="h-3 w-3" />
                              Complete
                            </button>
                          )
                        }
                        return row.assigneeId ? (
                          <button
                            onClick={() => {
                              if (workflow === 'FULL') {
                                setOrderTask(row.id)
                                setOrderForm({ orderId: '', vendor: '' })
                              } else {
                                setPendingTaskAction({
                                  type: 'ordered',
                                  taskId: row.id,
                                  workflow,
                                  description: `Mark task "${row.description}" as ${workflow === 'SOFTWARE' ? 'provisioned' : 'ordered'}?`,
                                })
                              }
                            }}
                            disabled={orderedMutation.isPending}
                            className={btn}
                          >
                            {workflow === 'SOFTWARE' ? 'Mark Provisioned' : 'Mark Ordered'}
                          </button>
                        ) : (
                          <span className="text-xs text-muted-foreground">Awaiting assignment</span>
                        )
                      }
                      if (row.status === 'ORDERED') {
                        return (
                          <button onClick={() => { setDeliveryDateTask(row.id); setDeliveryDate(''); setActionError(null) }} className={btn}>
                            {workflow === 'SOFTWARE' ? 'Set Effective Date' : 'Set Delivery Date'}
                          </button>
                        )
                      }
                      if (row.status === 'DELIVERY_DATE_SET') {
                        return (
                          <button onClick={() => setPendingTaskAction({ type: 'installed', taskId: row.id, workflow, description: `Mark task "${row.description}" as ${workflow === 'SOFTWARE' ? 'granted' : 'installed'}?` })} disabled={deliverMutation.isPending} className={btn}>
                            {workflow === 'SOFTWARE' ? 'Mark Granted' : 'Mark Installed'}
                          </button>
                        )
                      }
                      // DELIVERED
                      return (
                        <button onClick={() => { setCompleteTask(row.id); setClosingNotes(''); setActionError(null) }} className={btn}>
                          <CheckCircle2 className="h-3 w-3" />
                          Complete
                        </button>
                      )
                    },
                  },
                ]}
                data={request.tasks}
                getRowKey={(row) => row.id}
                emptyText="No fulfillment tasks."
              />
            </section>

            <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
              <h2 className="mb-4 text-lg font-semibold">Activity</h2>
              {activityQuery.isLoading ? (
                <p className="text-sm text-muted-foreground">Loading…</p>
              ) : (activityQuery.data ?? []).length === 0 ? (
                <p className="text-sm text-muted-foreground">No activity recorded yet.</p>
              ) : (
                <ol className="relative space-y-4 border-l border-border pl-5">
                  {(activityQuery.data ?? []).map((entry) => (
                    <li key={entry.id} className="relative">
                      <span className="absolute -left-[26px] top-1 h-2.5 w-2.5 rounded-full bg-primary" />
                      <div className="text-sm font-medium">
                        {actionLabel(entry.action)}
                      </div>
                      <div className="text-xs text-muted-foreground">
                        {entry.actorName ?? 'System'} · {formatDateTime(entry.createdAt)}
                      </div>
                      {describeAfter(entry.afterState) && (
                        <div className="mt-1 rounded-md bg-muted px-2 py-1 text-xs">
                          {describeAfter(entry.afterState)}
                        </div>
                      )}
                    </li>
                  ))}
                </ol>
              )}
            </section>

            <CommentThread
              baseUrl={`/api/v1/service-requests/${id}`}
              queryKey={`service-request-comments-${id}`}
              canPostInternal={canPostInternal}
            />
          </div>

          <aside className="space-y-6">
            {canPostInternal && ['APPROVED', 'IN_FULFILLMENT', 'ON_HOLD'].includes(request.status) && (
              <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
                <h2 className="mb-2 text-lg font-semibold">
                  {request.status === 'ON_HOLD' ? 'Resume Work' : 'Hold Request'}
                </h2>
                <p className="mb-3 text-sm text-muted-foreground">
                  {request.status === 'ON_HOLD'
                    ? 'Resume fulfillment — the SLA clock resumes from where it paused.'
                    : 'Pauses work and stops the SLA clock until the request resumes.'}
                </p>
                <button
                  onClick={() => statusMutation.mutate(request.status === 'ON_HOLD' ? 'IN_FULFILLMENT' : 'ON_HOLD')}
                  disabled={statusMutation.isPending}
                  className="w-full rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-muted disabled:opacity-50"
                >
                  {request.status === 'ON_HOLD' ? 'Resume (restart SLA clock)' : 'Place on Hold (pause SLA)'}
                </button>
              </div>
            )}

            {request.status === 'PENDING_APPROVAL' && isSuperAdmin && (
              <div className="rounded-xl border border-amber-300 bg-amber-50/50 p-6 shadow-sm dark:border-amber-800 dark:bg-amber-950/20">
                <h2 className="mb-2 text-lg font-semibold">Admin Override</h2>
                <p className="mb-3 text-sm text-muted-foreground">
                  Approve this request without the designated approver's decision. The record will show the
                  approval was bypassed — never attribute it to the approver. A reason is required.
                </p>
                <button
                  onClick={() => { setBypassReason(''); setActionError(null); setBypassOpen(true) }}
                  className="w-full rounded-md border border-amber-400 bg-amber-100 px-4 py-2 text-sm font-medium text-amber-900 transition hover:bg-amber-200 dark:border-amber-700 dark:bg-amber-900/40 dark:text-amber-200 dark:hover:bg-amber-900"
                >
                  Bypass Approval…
                </button>
              </div>
            )}

            {request.status === 'PENDING_APPROVAL' && request.approverId === currentUser?.id && (
              <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
                <h2 className="mb-4 text-lg font-semibold">Approval</h2>
                <div className="space-y-3">
                  <button
                    onClick={() => setApproveOpen(true)}
                    className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90"
                  >
                    Approve
                  </button>
                  <button
                    onClick={() => setRejectOpen(true)}
                    className="w-full rounded-md bg-destructive px-4 py-2 text-sm font-medium text-destructive-foreground transition hover:bg-destructive/90"
                  >
                    Reject
                  </button>
                </div>
              </div>
            )}

            {request.status !== 'PENDING_APPROVAL' && isAdminOrSuperAdmin && (
              <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
                <h2 className="mb-4 text-lg font-semibold">Approval</h2>
                <p className="mb-3 text-sm text-muted-foreground">Send this request to the location's approval manager retroactively.</p>
                <button
                  onClick={() => setSendToApprovalOpen(true)}
                  className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90"
                >
                  Send to Approval
                </button>
              </div>
            )}

            {request.status === 'PENDING_APPROVAL' && isAdminOrSuperAdmin && (
              <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
                <h2 className="mb-4 text-lg font-semibold">Send Reminder</h2>
                <textarea
                  value={reminderMessage}
                  onChange={(e) => setReminderMessage(e.target.value)}
                  className="mb-3 w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                  rows={4}
                  placeholder="Enter reminder message…"
                />
                <button
                  onClick={() => sendReminderMutation.mutate(reminderMessage)}
                  disabled={!reminderMessage.trim() || sendReminderMutation.isPending}
                  className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
                >
                  Send Reminder
                </button>
              </div>
            )}
          </aside>
        </div>
      </div>

      <FormDrawer open={approveOpen} title="Approve Request" dirty={approveForm.comment?.trim() !== '' && approveForm.comment !== undefined} onClose={() => { setApproveOpen(false); setActionError(null) }}>
        <EntityForm
          fields={[{ name: 'comment', label: 'Comment (required)', type: 'textarea', required: true }]}
          values={approveForm}
          onChange={(name, value) => setApproveForm({ ...approveForm, [name]: value })}
          onSubmit={(e) => {
            e.preventDefault()
            if (!approveForm.comment.trim()) {
              setActionError('A comment is required when approving a request')
              return
            }
            approveMutation.mutate(approveForm.comment)
          }}
          submitLabel="Approve"
          pending={approveMutation.isPending}
        />
        {actionError && <p className="mt-4 text-sm text-destructive">{actionError}</p>}
      </FormDrawer>

      <FormDrawer open={rejectOpen} title="Reject Request" dirty={rejectForm.comment?.trim() !== '' && rejectForm.comment !== undefined} onClose={() => { setRejectOpen(false); setActionError(null) }}>
        <EntityForm
          fields={[{ name: 'comment', label: 'Reason', type: 'textarea', required: true }]}
          values={rejectForm}
          onChange={(name, value) => setRejectForm({ ...rejectForm, [name]: value })}
          onSubmit={(e) => {
            e.preventDefault()
            if (!rejectForm.comment.trim()) {
              setActionError('A reason is required when rejecting a request')
              return
            }
            rejectMutation.mutate(rejectForm.comment)
          }}
          submitLabel="Reject"
          pending={rejectMutation.isPending}
        />
        {actionError && <p className="mt-4 text-sm text-destructive">{actionError}</p>}
      </FormDrawer>

      <FormDrawer open={sendToApprovalOpen} title="Send to Approval" dirty={sendToApprovalReason.trim() !== ''} onClose={() => { setSendToApprovalOpen(false); setSendToApprovalReason(''); setActionError(null) }}>
        <EntityForm
          fields={[{ name: 'reason', label: 'Reason approval is now required', type: 'textarea', required: true }]}
          values={{ reason: sendToApprovalReason }}
          onChange={(_, value) => setSendToApprovalReason(value)}
          onSubmit={(e) => {
            e.preventDefault()
            if (!sendToApprovalReason.trim()) {
              setActionError('A reason is required when sending to approval')
              return
            }
            sendToApprovalMutation.mutate(sendToApprovalReason)
          }}
          submitLabel="Confirm"
          pending={sendToApprovalMutation.isPending}
        />
        {actionError && <p className="mt-4 text-sm text-destructive">{actionError}</p>}
      </FormDrawer>

      <FormDrawer open={bypassOpen} title="Bypass Approval" dirty={bypassReason.trim() !== ''} onClose={() => { setBypassOpen(false); setBypassReason(''); setActionError(null) }}>
        <EntityForm
          fields={[{ name: 'reason', label: 'Reason for bypassing approval (required)', type: 'textarea', required: true }]}
          values={{ reason: bypassReason }}
          onChange={(_, value) => setBypassReason(value)}
          onSubmit={(e) => {
            e.preventDefault()
            if (!bypassReason.trim()) {
              setActionError('A reason is required when bypassing approval')
              return
            }
            bypassMutation.mutate(bypassReason)
          }}
          submitLabel="Bypass & Approve"
          pending={bypassMutation.isPending}
        />
        {actionError && <p className="mt-4 text-sm text-destructive">{actionError}</p>}
      </FormDrawer>

      <FormDrawer open={editOpen} title="Edit Request (admin)" dirty onClose={() => { setEditOpen(false); setActionError(null) }}>
        <div className="space-y-4">
          <div className="space-y-2">
            <label htmlFor="edit-location" className="text-sm font-medium">Location</label>
            <select
              id="edit-location"
              value={editForm.locationId}
              onChange={(e) => setEditForm({ ...editForm, locationId: e.target.value })}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            >
              {(locationsQuery.data ?? []).map((l) => (
                <option key={l.id} value={l.id}>{l.name}</option>
              ))}
            </select>
            {request.status === 'PENDING_APPROVAL' && (
              <p className="text-xs text-muted-foreground">
                Changing the location re-routes the pending approval to the new location's approval manager.
              </p>
            )}
          </div>
          <div className="space-y-2">
            <label htmlFor="edit-priority" className="text-sm font-medium">Priority</label>
            <select
              id="edit-priority"
              value={editForm.priorityId}
              onChange={(e) => setEditForm({ ...editForm, priorityId: e.target.value })}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            >
              <option value="">Default</option>
              {(prioritiesQuery.data ?? []).map((p) => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
          </div>
          <div className="space-y-2">
            <label htmlFor="edit-phone" className="text-sm font-medium">Phone Number</label>
            <input
              id="edit-phone"
              type="tel"
              value={editForm.phone}
              onChange={(e) => setEditForm({ ...editForm, phone: e.target.value })}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="edit-neededby" className="text-sm font-medium">Needed By</label>
            <DateInput
              id="edit-neededby"
              value={editForm.neededBy}
              onChange={(e) => setEditForm({ ...editForm, neededBy: e.target.value })}
            />
          </div>
          {catalogItemQuery.data?.formSchema && (() => {
            let parsed: unknown
            try { parsed = JSON.parse(catalogItemQuery.data.formSchema) } catch { parsed = null }
            return isValidSchema(parsed) ? (
              <SchemaForm
                schema={parsed}
                values={editFormData}
                onChange={(name, value) => setEditFormData({ ...editFormData, [name]: value })}
                onSubmit={() => updateMutation.mutate()}
                submitLabel="Save Changes"
                pending={updateMutation.isPending}
                serverError={actionError}
              />
            ) : null
          })()}
          {(!catalogItemQuery.data?.formSchema) && (
            <>
              <button
                onClick={() => updateMutation.mutate()}
                disabled={updateMutation.isPending}
                className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
              >
                Save Changes
              </button>
              {actionError && <p className="text-sm text-destructive">{actionError}</p>}
            </>
          )}
        </div>
      </FormDrawer>

      <FormDrawer open={deliveryDateTask !== null} title="Set Delivery Date" dirty={deliveryDate !== ''} onClose={() => { setDeliveryDateTask(null); setActionError(null) }}>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            if (!deliveryDate) {
              setActionError('A delivery date is required')
              return
            }
            deliveryDateMutation.mutate({ taskId: deliveryDateTask!, date: deliveryDate })
          }}
          className="space-y-4"
        >
          <div>
            <label htmlFor="expected-delivery-date" className="mb-1 block text-sm font-medium">
              Expected delivery date <span className="text-destructive">*</span>
            </label>
            <DateInput
              id="expected-delivery-date"
              required
              value={deliveryDate}
              onChange={(e) => setDeliveryDate(e.target.value)}
            />
          </div>
          <button
            type="submit"
            disabled={deliveryDateMutation.isPending}
            className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
          >
            Set Delivery Date
          </button>
        </form>
        {actionError && <p className="mt-4 text-sm text-destructive">{actionError}</p>}
      </FormDrawer>

      <FormDrawer open={orderTask !== null} title="Mark Ordered" dirty={orderForm.orderId.trim() !== '' || orderForm.vendor.trim() !== ''} onClose={() => { setOrderTask(null); setActionError(null) }}>
        <EntityForm
          fields={[
            { name: 'orderId', label: 'Order ID (optional)', type: 'text' },
            { name: 'vendor', label: 'Vendor (optional)', type: 'text' },
          ]}
          values={orderForm}
          onChange={(name, value) => setOrderForm({ ...orderForm, [name]: value })}
          onSubmit={(e) => {
            e.preventDefault()
            orderedMutation.mutate({ taskId: orderTask!, orderId: orderForm.orderId, vendor: orderForm.vendor })
          }}
          submitLabel="Mark Ordered"
          pending={orderedMutation.isPending}
        />
        {actionError && <p className="mt-4 text-sm text-destructive">{actionError}</p>}
      </FormDrawer>

      <FormDrawer open={completeTask !== null} title="Complete Task" dirty={closingNotes.trim() !== ''} onClose={() => { setCompleteTask(null); setActionError(null) }}>
        <EntityForm
          fields={[{ name: 'closingNotes', label: 'Closing notes (required)', type: 'textarea', required: true }]}
          values={{ closingNotes }}
          onChange={(_name, value) => setClosingNotes(value)}
          onSubmit={(e) => {
            e.preventDefault()
            if (!closingNotes.trim()) {
              setActionError('Closing notes are required when completing a task')
              return
            }
            taskMutation.mutate({ taskId: completeTask!, closingNotes: closingNotes.trim() })
          }}
          submitLabel="Complete"
          pending={taskMutation.isPending}
        />
        {actionError && <p className="mt-4 text-sm text-destructive">{actionError}</p>}
      </FormDrawer>
    </div>
  )
}
