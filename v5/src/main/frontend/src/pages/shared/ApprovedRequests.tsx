import { useLocation, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useMsal } from '@azure/msal-react'
import { ArrowLeft } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useSmartBack } from '../../lib/useSmartBack'
import { DataTable } from '../../components/ui/DataTable'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { formatDate, formatDateTime } from '../../lib/date'

interface ApprovedRequest {
  id: string
  number: string
  catalogItemName: string
  requesterName: string
  locationName: string | null
  status: string
  decidedAt: string
  createdAt: string
}

export function ApprovedRequests() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const navigate = useNavigate()
  const location = useLocation()
  const isHome = location.pathname.startsWith('/home')
  const backPath = isHome ? '/home' : '/dashboard'
  const smartBack = useSmartBack(backPath)
  const detailPrefix = isHome ? '/home/approved-requests' : '/dashboard/approved-requests'

  const query = useQuery<ApprovedRequest[]>({
    queryKey: ['approved-requests'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/service-requests/approved')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  if (query.isLoading) return <Loading />
  if (query.error) return <ErrorFallback error={query.error} message="Could not load approved requests." onRetry={() => query.refetch()} />

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex items-center gap-4">
          <button
            onClick={smartBack}
            className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </button>
          <h1 className="text-2xl font-semibold tracking-tight">Requests I Approved</h1>
        </div>
        <p className="text-sm text-muted-foreground">
          Read-only view of service requests you have already approved. Decisions made here cannot be changed.
        </p>

        <DataTable<ApprovedRequest>
          caption="Requests I approved"
          columns={[
            { key: 'number', header: 'Number' },
            { key: 'catalogItemName', header: 'Catalog Item' },
            { key: 'requesterName', header: 'Requester' },
            { key: 'locationName', header: 'Location', render: (row) => row.locationName ?? '—' },
            { key: 'status', header: 'Current Status', render: (row) => <StatusBadge status={row.status} /> },
            { key: 'decidedAt', header: 'Approved At', render: (row) => (row.decidedAt ? formatDateTime(row.decidedAt) : '—') },
            { key: 'createdAt', header: 'Submitted', render: (row) => formatDate(row.createdAt) },
          ]}
          data={query.data ?? []}
          getRowKey={(row) => row.id}
          onRowClick={(row) => navigate(`${detailPrefix}/${row.id}`)}
          emptyText="No approved requests yet."
        />
      </div>
    </div>
  )
}
