import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useMsal } from '@azure/msal-react'
import { Plus } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { DataTable } from '../../components/ui/DataTable'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'

export interface ServiceRequest {
  id: string
  number: number
  catalogItemName: string
  requesterName: string
  status: string
  approvalDecision: string
}

export function ServiceRequestList() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]

  const query = useQuery<ServiceRequest[]>({
    queryKey: ['service-requests'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/service-requests')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  if (query.isLoading) return <Loading />
  if (query.error) return <ErrorFallback error={query.error} message="Could not load service requests." onRetry={() => query.refetch()} />

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold tracking-tight">Service Requests</h1>
          <Link
            to="/dashboard/service-requests/new"
            className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <Plus className="h-4 w-4" />
            New Request
          </Link>
        </div>
        <DataTable<ServiceRequest>
          caption="List of service requests"
          columns={[
            { key: 'number', header: 'Number' },
            { key: 'catalogItemName', header: 'Catalog Item' },
            { key: 'requesterName', header: 'Requester' },
            { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
            { key: 'approvalDecision', header: 'Approval', render: (row) => <StatusBadge status={row.approvalDecision} /> },
            { key: 'title', header: 'Title', render: (row) => <Link to={`/dashboard/service-requests/${row.id}`} className="font-medium hover:underline">View</Link> },
          ]}
          data={query.data ?? []}
          getRowKey={(row) => row.id}
          emptyText="No service requests found."
        />
      </div>
    </div>
  )
}
