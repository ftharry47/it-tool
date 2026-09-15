import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useMsal } from '@azure/msal-react'
import { Plus } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { DataTable } from '../../components/ui/DataTable'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { FilterBar, FilterSelect, useSessionFilters, enumLabel } from '../../components/ui/FilterBar'

const SR_STATUSES = ['SUBMITTED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'IN_FULFILLMENT', 'FULFILLED', 'CANCELLED']
const APPROVAL_DECISIONS = ['PENDING', 'APPROVED', 'REJECTED']

export interface ServiceRequest {
  id: string
  number: number
  catalogItemName: string
  requesterName: string
  status: string
  approvalDecision: string
  locationName: string | null
}

export function ServiceRequestList() {
  const navigate = useNavigate()
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

  const { filters, setFilter, clearFilters, activeCount } = useSessionFilters('sr-filters', {
    status: '',
    catalogItem: '',
    location: '',
    approval: '',
  })

  const requests = query.data ?? []
  const catalogItems = Array.from(new Set(requests.map((r) => r.catalogItemName))).sort()
  const locations = Array.from(new Set(requests.map((r) => r.locationName).filter((l): l is string => !!l))).sort()
  const filtered = requests.filter((r) => {
    if (filters.status && r.status !== filters.status) return false
    if (filters.catalogItem && r.catalogItemName !== filters.catalogItem) return false
    if (filters.location && r.locationName !== filters.location) return false
    if (filters.approval && r.approvalDecision !== filters.approval) return false
    return true
  })

  if (query.isLoading) return <Loading />
  if (query.error) return <ErrorFallback error={query.error} message="Could not load service requests." onRetry={() => query.refetch()} />

  const emptyText = requests.length === 0 ? 'No service requests found.' : 'No requests match the selected filters.'

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold tracking-tight">Service Requests</h1>
          <div className="flex items-center gap-2">
            <Link
              to="/dashboard/service-requests/new"
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <Plus className="h-4 w-4" />
              New Request
            </Link>
          </div>
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
          <FilterSelect
            label="Approval"
            value={filters.approval}
            options={APPROVAL_DECISIONS.map((d) => ({ value: d, label: enumLabel(d) }))}
            onChange={(v) => setFilter('approval', v)}
          />
        </FilterBar>

        <DataTable<ServiceRequest>
          caption="List of service requests"
          columns={[
            { key: 'number', header: 'Number' },
            { key: 'catalogItemName', header: 'Catalog Item' },
            { key: 'requesterName', header: 'Requester' },
            { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
            { key: 'approvalDecision', header: 'Approval', render: (row) => <StatusBadge status={row.approvalDecision} /> },
            { key: 'locationName', header: 'Location', render: (row) => row.locationName ?? '—' },
          ]}
          data={filtered}
          getRowKey={(row) => row.id}
          onRowClick={(row) => navigate(`/dashboard/service-requests/${row.id}`)}
          emptyText={emptyText}
        />
      </div>
    </div>
  )
}
