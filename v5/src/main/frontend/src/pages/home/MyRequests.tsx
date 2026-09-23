import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useMsal } from '@azure/msal-react'
import { ArrowLeft, Plus } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { DataTable } from '../../components/ui/DataTable'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { FilterBar, FilterSelect, useSessionFilters, enumLabel } from '../../components/ui/FilterBar'
import { formatDate } from '../../lib/date'
import { useSmartBack } from '../../lib/useSmartBack'

const SR_STATUSES = ['SUBMITTED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'IN_FULFILLMENT', 'FULFILLED', 'CANCELLED']

interface MyServiceRequest {
  id: string
  number: number
  catalogItemName: string
  status: string
  locationName: string | null
  createdAt: string
}

export function MyRequests() {
  const smartBack = useSmartBack('/home')
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const navigate = useNavigate()

  const query = useQuery<MyServiceRequest[]>({
    queryKey: ['my-service-requests'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/service-requests/my')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const { filters, setFilter, clearFilters, activeCount } = useSessionFilters('my-sr-filters', {
    status: '',
    catalogItem: '',
    location: '',
  })

  const requests = query.data ?? []
  const catalogItems = Array.from(new Set(requests.map((r) => r.catalogItemName))).sort()
  const locations = Array.from(new Set(requests.map((r) => r.locationName).filter((l): l is string => !!l))).sort()
  const filtered = requests.filter((r) => {
    if (filters.status && r.status !== filters.status) return false
    if (filters.catalogItem && r.catalogItemName !== filters.catalogItem) return false
    if (filters.location && r.locationName !== filters.location) return false
    return true
  })

  if (query.isLoading) return <Loading />
  if (query.error) return <ErrorFallback error={query.error} message="Could not load your requests." onRetry={() => query.refetch()} />

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex items-center justify-between">
          <button
            onClick={smartBack}
            className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </button>
          <h1 className="text-2xl font-semibold tracking-tight">My Requests</h1>
          <Link
            to="/home/catalog"
            className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <Plus className="h-4 w-4" />
            New Request
          </Link>
        </div>
        <FilterBar activeCount={activeCount} onClear={clearFilters}>
          <FilterSelect
            label="Status"
            value={filters.status}
            options={SR_STATUSES.map((s) => ({ value: s, label: enumLabel(s) }))}
            onChange={(v) => setFilter('status', v)}
          />
          <FilterSelect
            label="Catalog Item"
            value={filters.catalogItem}
            options={catalogItems.map((c) => ({ value: c, label: c }))}
            onChange={(v) => setFilter('catalogItem', v)}
          />
          <FilterSelect
            label="Location"
            value={filters.location}
            options={locations.map((l) => ({ value: l, label: l }))}
            onChange={(v) => setFilter('location', v)}
          />
        </FilterBar>
        <DataTable<MyServiceRequest>
          caption="Your service requests"
          columns={[
            { key: 'number', header: 'Number' },
            { key: 'catalogItemName', header: 'Catalog Item' },
            { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
            { key: 'locationName', header: 'Location', render: (row) => row.locationName ?? '—' },
            { key: 'createdAt', header: 'Submitted', render: (row) => formatDate(row.createdAt) },
          ]}
          data={filtered}
          getRowKey={(row) => row.id}
          onRowClick={(row) => navigate(`/home/service-requests/${row.id}`)}
          emptyText={requests.length === 0 ? "You haven't submitted any requests yet." : 'No requests match the selected filters.'}
        />
      </div>
    </div>
  )
}
