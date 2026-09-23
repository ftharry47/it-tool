import { useParams, useLocation } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, CheckCircle2, Circle } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useDocumentTitle } from '../../components/layout/useDocumentTitle'
import { useSmartBack } from '../../lib/useSmartBack'
import { CommentThread } from '../../components/ui/CommentThread'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { Loading } from '../../components/ui/Loading'
import { StatusBadge, formatStatusLabel } from '../../components/ui/StatusBadge'
import { formatDate, formatDateTime } from '../../lib/date'

interface FulfillmentTask {
  id: string
  description: string
  sequenceOrder: number
  status: string
  expectedDeliveryDate: string | null
  deliveredAt: string | null
}

interface ActivityEntry {
  id: string
  action: string
  actorName: string | null
  afterState: string | null
  createdAt: string
}

interface ServiceRequestDetail {
  id: string
  number: string
  catalogItemName: string
  status: string
  requesterName: string
  approvalDecision: string
  approvalComment: string | null
  neededBy: string | null
  locationName: string | null
  decidedAt: string | null
  tasks: FulfillmentTask[]
  createdAt: string
}

const ACTION_LABELS: Record<string, string> = {
  SUBMITTED: 'Submitted',
  ROUTED_TO_APPROVER: 'Sent for approval',
  SENT_TO_APPROVAL: 'Sent to approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  IN_FULFILLMENT: 'Being fulfilled',
  FULFILLED: 'Fulfilled',
  TASK_ASSIGNED: 'Fulfiller assigned',
  TASK_ORDERED: 'Order placed',
  DELIVERY_DATE_SET: 'Delivery date set',
  DELIVERED: 'Installed',
  TASK_COMPLETED: 'Task completed',
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
    return null
  } catch {
    return null
  }
}

export function ApprovedRequestDetail() {
  const { id } = useParams<{ id: string }>()
  const location = useLocation()
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const isHome = location.pathname.startsWith('/home')
  const listPath = isHome ? '/home/approved-requests' : '/dashboard/approved-requests'
  const smartBack = useSmartBack(listPath)

  const requestQuery = useQuery<ServiceRequestDetail>({
    queryKey: ['approved-request', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!id && !!account,
  })

  const activityQuery = useQuery<ActivityEntry[]>({
    queryKey: ['approved-request-activity', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}/activity`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!id && !!account,
  })

  useDocumentTitle(
    requestQuery.data ? `Approved Request #${requestQuery.data.number}` : 'Approved Request'
  )

  if (requestQuery.isLoading) return <Loading />
  if (requestQuery.error) return <ErrorFallback error={requestQuery.error} message="Could not load the approved request." onRetry={() => requestQuery.refetch()} />
  if (!requestQuery.data) return <Loading />

  const request = requestQuery.data
  const sortedTasks = [...request.tasks].sort((a, b) => a.sequenceOrder - b.sequenceOrder)

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-4xl space-y-6">
        <div className="flex items-center gap-4">
          <button
            onClick={smartBack}
            className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </button>
          <h1 className="text-2xl font-semibold tracking-tight">
            Request #{request.number} — {request.catalogItemName}
          </h1>
        </div>

        <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
          Read-only view: this request has already been decided. No status or fulfillment actions are available.
        </div>

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold">Details</h2>
          <dl className="grid gap-2 text-sm sm:grid-cols-2">
            <div className="flex justify-between sm:block">
              <dt className="text-muted-foreground">Status</dt>
              <dd><StatusBadge status={request.status} /></dd>
            </div>
            <div className="flex justify-between sm:block">
              <dt className="text-muted-foreground">Approval Decision</dt>
              <dd><StatusBadge status={request.approvalDecision} /></dd>
            </div>
            <div className="flex justify-between sm:block">
              <dt className="text-muted-foreground">Requester</dt>
              <dd className="font-medium">{request.requesterName}</dd>
            </div>
            <div className="flex justify-between sm:block">
              <dt className="text-muted-foreground">Location</dt>
              <dd className="font-medium">{request.locationName ?? '—'}</dd>
            </div>
            <div className="flex justify-between sm:block">
              <dt className="text-muted-foreground">Submitted</dt>
              <dd className="font-medium">{formatDateTime(request.createdAt)}</dd>
            </div>
            {request.neededBy && (
              <div className="flex justify-between sm:block">
                <dt className="text-muted-foreground">Needed by</dt>
                <dd className="font-medium">{formatDate(request.neededBy)}</dd>
              </div>
            )}
            {request.approvalComment && (
              <div className="sm:col-span-2">
                <dt className="text-muted-foreground">Approver note</dt>
                <dd className="mt-1 rounded-md bg-muted p-2">{request.approvalComment}</dd>
              </div>
            )}
          </dl>
        </section>

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold">Progress</h2>
          {sortedTasks.length === 0 ? (
            <p className="text-sm text-muted-foreground">No fulfillment tasks yet.</p>
          ) : (
            <ul className="space-y-3">
              {sortedTasks.map((task) => (
                <li key={task.id} className="flex items-start gap-3 text-sm">
                  {task.status === 'COMPLETED' ? (
                    <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
                  ) : (
                    <Circle className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
                  )}
                  <div className="flex-1">
                    <span className={task.status === 'COMPLETED' ? 'text-muted-foreground line-through' : 'font-medium'}>
                      {task.description}
                    </span>
                    {task.expectedDeliveryDate && task.status !== 'COMPLETED' && (
                      <span className="ml-2 text-xs text-muted-foreground">
                        Expected {formatDate(task.expectedDeliveryDate)}
                      </span>
                    )}
                  </div>
                  <StatusBadge status={task.status} />
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold">History</h2>
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
                    {entry.actorName ? `${entry.actorName} · ` : ''}{formatDateTime(entry.createdAt)}
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
          queryKey={`approved-request-comments-${id}`}
          canPostInternal={true}
        />
      </div>
    </div>
  )
}
