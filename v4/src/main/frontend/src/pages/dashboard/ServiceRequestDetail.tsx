import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, CheckCircle2 } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { DataTable } from '../../components/ui/DataTable'
import { EntityForm } from '../../components/ui/EntityForm'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { FormDrawer } from '../../components/ui/FormDrawer'
import { Loading } from '../../components/ui/Loading'
import { StatusBadge } from '../../components/ui/StatusBadge'

interface FulfillmentTask {
  id: string
  description: string
  sequenceOrder: number
  status: string
}

interface ServiceRequestDetail {
  id: string
  number: number
  catalogItemName: string
  requesterName: string
  status: string
  formData: string
  approvalRequired: boolean
  approverName: string | null
  approvalDecision: string
  approvalComment: string | null
  decidedAt: string | null
  neededBy: string | null
  tasks: FulfillmentTask[]
}

export function ServiceRequestDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { instance, accounts } = useMsal()
  const account = accounts[0]

  const [approveOpen, setApproveOpen] = useState(false)
  const [rejectOpen, setRejectOpen] = useState(false)
  const [approveForm, setApproveForm] = useState({ comment: '' })
  const [rejectForm, setRejectForm] = useState({ comment: '' })
  const [actionError, setActionError] = useState<string | null>(null)

  const requestQuery = useQuery<ServiceRequestDetail>({
    queryKey: ['service-request', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}`)
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
      queryClient.invalidateQueries({ queryKey: ['service-requests'] })
    },
    onError: (error) => setActionError(error.message),
  })

  const taskMutation = useMutation<ServiceRequestDetail, Error, string>({
    mutationFn: async (taskId) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests/${id}/tasks/${taskId}/complete`, {
        method: 'POST',
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['service-request', id] })
      queryClient.invalidateQueries({ queryKey: ['service-requests'] })
    },
  })

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

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-5xl space-y-6">
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate('/dashboard/service-requests')}
            className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </button>
          <h1 className="text-2xl font-semibold tracking-tight">
            Request #{request.number} — {request.catalogItemName}
          </h1>
        </div>

        <div className="grid gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">
            <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
              <h2 className="mb-4 text-lg font-semibold">Details</h2>
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
                  <dt className="text-muted-foreground">Approver</dt>
                  <dd className="font-medium">{request.approverName ?? '—'}</dd>
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
                    key: 'actions',
                    header: 'Actions',
                    render: (row) =>
                      row.status !== 'COMPLETED' ? (
                        <button
                          onClick={() => taskMutation.mutate(row.id)}
                          disabled={taskMutation.isPending}
                          className="inline-flex items-center gap-1 rounded-md bg-primary px-2 py-1 text-xs font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
                        >
                          <CheckCircle2 className="h-3 w-3" />
                          Complete
                        </button>
                      ) : (
                        <span className="text-xs text-muted-foreground">Done</span>
                      ),
                  },
                ]}
                data={request.tasks}
                getRowKey={(row) => row.id}
                emptyText="No fulfillment tasks."
              />
            </section>
          </div>

          <aside className="space-y-6">
            {request.approvalRequired && request.approvalDecision === 'PENDING' && (
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
          </aside>
        </div>
      </div>

      <FormDrawer open={approveOpen} title="Approve Request" onClose={() => { setApproveOpen(false); setActionError(null) }}>
        <EntityForm
          fields={[{ name: 'comment', label: 'Comment', type: 'textarea' }]}
          values={approveForm}
          onChange={(name, value) => setApproveForm({ ...approveForm, [name]: value })}
          onSubmit={(e) => {
            e.preventDefault()
            approveMutation.mutate(approveForm.comment)
          }}
          submitLabel="Approve"
          pending={approveMutation.isPending}
        />
        {actionError && <p className="mt-4 text-sm text-destructive">{actionError}</p>}
      </FormDrawer>

      <FormDrawer open={rejectOpen} title="Reject Request" onClose={() => { setRejectOpen(false); setActionError(null) }}>
        <EntityForm
          fields={[{ name: 'comment', label: 'Comment (optional)', type: 'textarea' }]}
          values={rejectForm}
          onChange={(name, value) => setRejectForm({ ...rejectForm, [name]: value })}
          onSubmit={(e) => {
            e.preventDefault()
            rejectMutation.mutate(rejectForm.comment)
          }}
          submitLabel="Reject"
          pending={rejectMutation.isPending}
        />
        {actionError && <p className="mt-4 text-sm text-destructive">{actionError}</p>}
      </FormDrawer>
    </div>
  )
}
