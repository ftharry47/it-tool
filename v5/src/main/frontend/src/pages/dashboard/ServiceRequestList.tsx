import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMsal } from '@azure/msal-react'
import { Plus } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { DataTable } from '../../components/ui/DataTable'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { FilterBar, FilterSelect, useSessionFilters, enumLabel } from '../../components/ui/FilterBar'
import { BulkActionToolbar } from '../../components/ui/BulkActionToolbar'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { ToastStack, type ToastItem } from '../../components/ui/Toast'

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
  const queryClient = useQueryClient()
  const { instance, accounts } = useMsal()
  const account = accounts[0]

  const [showDeleted, setShowDeleted] = useState(false)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const [confirm, setConfirm] = useState<{ open: boolean; action: 'delete' | 'restore' } | null>(null)

  const pushToast = (type: ToastItem['type'], message: string) => {
    setToasts((prev) => [...prev, { id: crypto.randomUUID(), type, message }])
  }
  const dismissToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }

  const query = useQuery<ServiceRequest[]>({
    queryKey: ['service-requests', showDeleted],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests?showDeleted=${showDeleted}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const bulkAction = useMutation<{ action: 'delete' | 'restore' }, Error, void>({
    mutationFn: async () => {
      const ids = Array.from(selectedIds)
      const action = confirm?.action ?? 'delete'
      const url = `/api/v1/bulk/service-requests/${action === 'delete' ? 'soft-delete' : 'restore'}`
      const res = await fetchWithToken(instance, account!, url, {
        method: 'POST',
        body: JSON.stringify(ids),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return { action }
    },
    onSuccess: ({ action }) => {
      setSelectedIds(new Set())
      queryClient.invalidateQueries({ queryKey: ['service-requests'] })
      pushToast('success', action === 'delete' ? 'Selected requests deleted.' : 'Selected requests restored.')
      setConfirm(null)
    },
    onError: (err) => {
      pushToast('error', `Bulk ${confirm?.action} failed: ${err.message}`)
      setConfirm(null)
    },
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

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <ToastStack toasts={toasts} onDismiss={dismissToast} />
      <ConfirmDialog
        open={!!confirm}
        title={confirm?.action === 'delete' ? 'Delete selected requests?' : 'Restore selected requests?'}
        description={
          confirm?.action === 'delete'
            ? `Delete ${selectedIds.size} requests? This can be undone via Restore.`
            : `Restore ${selectedIds.size} requests? They will be visible again in all views and reports.`
        }
        confirmLabel={confirm?.action === 'delete' ? 'Delete' : 'Restore'}
        destructive={confirm?.action === 'delete'}
        onConfirm={() => bulkAction.mutate()}
        onCancel={() => setConfirm(null)}
      />
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
        <BulkActionToolbar
          selectedCount={selectedIds.size}
          showDeleted={showDeleted}
          onToggleShowDeleted={() => { setShowDeleted((v) => !v); setSelectedIds(new Set()) }}
          onDelete={() => setConfirm({ open: true, action: 'delete' })}
          onRestore={() => setConfirm({ open: true, action: 'restore' })}
        />
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
          selectable
          selectedIds={selectedIds}
          onSelectionChange={setSelectedIds}
          emptyText={requests.length === 0 ? 'No service requests found.' : 'No requests match the selected filters.'}
        />
      </div>
    </div>
  )
}
