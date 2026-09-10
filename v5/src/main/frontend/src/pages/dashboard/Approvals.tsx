import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, XCircle } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { DataTable } from '../../components/ui/DataTable'
import { EntityForm } from '../../components/ui/EntityForm'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { FormDrawer } from '../../components/ui/FormDrawer'
import { Loading } from '../../components/ui/Loading'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { formatDateTime } from '../../lib/date'

interface PendingApproval {
  id: string
  number: string
  catalogItemName: string
  requesterName: string
  status: string
  neededBy: string | null
  createdAt: string
}

export function Approvals() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const { currentUser } = useAuth()
  const isStaff = (currentUser?.roles ?? []).some((r) =>
    ['AGENT', 'TEAM_LEAD', 'ADMIN', 'SUPER_ADMIN'].includes(r)
  )
  const detailPath = (id: string) =>
    isStaff ? `/dashboard/service-requests/${id}` : `/home/approvals/${id}`

  const [deciding, setDeciding] = useState<{ id: string; approve: boolean } | null>(null)
  const [comment, setComment] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  const queueQuery = useQuery<PendingApproval[]>({
    queryKey: ['approvals-mine'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/service-requests/approvals/mine')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const decideMutation = useMutation<unknown, Error, { id: string; approve: boolean; comment: string }>({
    mutationFn: async ({ id, approve, comment }) => {
      const res = await fetchWithToken(
        instance,
        account!,
        `/api/v1/service-requests/${id}/${approve ? 'approve' : 'reject'}`,
        { method: 'POST', body: JSON.stringify({ comment, approve }) }
      )
      if (!res.ok) {
        const body = await res.json().catch(() => null)
        throw new Error(body?.error ?? `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      setDeciding(null)
      setComment('')
      setActionError(null)
      queryClient.invalidateQueries({ queryKey: ['approvals-mine'] })
      queryClient.invalidateQueries({ queryKey: ['service-requests'] })
    },
    onError: (error) => setActionError(error.message),
  })

  const submitDecision = () => {
    if (!deciding) return
    if (!comment.trim()) {
      setActionError('A comment is required when approving or rejecting a request')
      return
    }
    decideMutation.mutate({ id: deciding.id, approve: deciding.approve, comment: comment.trim() })
  }

  if (queueQuery.isLoading) return <Loading />
  if (queueQuery.error) return <ErrorFallback error={queueQuery.error} message="Could not load approvals." onRetry={() => queueQuery.refetch()} />

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-5xl space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">Approvals</h1>
        <p className="text-sm text-muted-foreground">
          Service requests waiting for your approval.
        </p>

        <DataTable<PendingApproval>
          caption="Requests pending your approval"
          columns={[
            { key: 'number', header: 'Request' },
            { key: 'catalogItemName', header: 'Catalog Item' },
            { key: 'requesterName', header: 'Requester' },
            { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
            {
              key: 'createdAt',
              header: 'Submitted',
              render: (row) => formatDateTime(row.createdAt),
            },
            {
              key: 'actions',
              header: 'Actions',
              render: (row) => (
                <div className="flex gap-2">
                  <button
                    onClick={() => navigate(detailPath(row.id))}
                    className="rounded-md border border-border px-2 py-1 text-xs hover:bg-muted"
                  >
                    View
                  </button>
                  <button
                    onClick={() => { setDeciding({ id: row.id, approve: true }); setComment(''); setActionError(null) }}
                    className="inline-flex items-center gap-1 rounded-md bg-primary px-2 py-1 text-xs font-medium text-primary-foreground hover:bg-primary/90"
                  >
                    <CheckCircle2 className="h-3 w-3" />
                    Approve
                  </button>
                  <button
                    onClick={() => { setDeciding({ id: row.id, approve: false }); setComment(''); setActionError(null) }}
                    className="inline-flex items-center gap-1 rounded-md bg-destructive px-2 py-1 text-xs font-medium text-destructive-foreground hover:bg-destructive/90"
                  >
                    <XCircle className="h-3 w-3" />
                    Reject
                  </button>
                </div>
              ),
            },
          ]}
          data={queueQuery.data ?? []}
          getRowKey={(row) => row.id}
          emptyText="No requests are waiting for your approval."
        />
      </div>

      <FormDrawer
        open={deciding !== null}
        title={deciding?.approve ? 'Approve Request' : 'Reject Request'}
        dirty={comment.trim() !== ''}
        onClose={() => { setDeciding(null); setActionError(null) }}
      >
        <EntityForm
          fields={[{
            name: 'comment',
            label: 'Comment (required)',
            type: 'textarea',
            required: true,
          }]}
          values={{ comment }}
          onChange={(_name, value) => setComment(value)}
          onSubmit={(e) => {
            e.preventDefault()
            submitDecision()
          }}
          submitLabel={deciding?.approve ? 'Approve' : 'Reject'}
          pending={decideMutation.isPending}
        />
        {actionError && <p className="mt-4 text-sm text-destructive">{actionError}</p>}
      </FormDrawer>
    </div>
  )
}
