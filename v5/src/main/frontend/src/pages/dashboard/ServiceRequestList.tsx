import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMsal } from '@azure/msal-react'
import { Plus, Trash2, RotateCcw, CheckSquare, XSquare } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
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
  const { currentUser } = useAuth()
  const isAdmin = currentUser?.roles.some((r) => ['ADMIN', 'SUPER_ADMIN'].includes(r)) ?? false

  const [view, setView] = useState<'active' | 'deleted'>('active')
  const [selectionMode, setSelectionMode] = useState(false)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const [confirm, setConfirm] = useState<{ open: boolean; action: 'delete' | 'restore' } | null>(null)

  const pushToast = (type: ToastItem['type'], message: string) => {
    setToasts((prev) => [...prev, { id: crypto.randomUUID(), type, message }])
  }
  const dismissToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }

  const exitSelection = () => {
    setSelectionMode(false)
    setSelectedIds(new Set())
  }

  const query = useQuery<ServiceRequest[]>({
    queryKey: ['service-requests', view],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/service-requests?showDeleted=${view === 'deleted'}`)
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
      exitSelection()
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

  const title = view === 'deleted' ? 'Deleted Service Requests' : 'Service Requests'
  const emptyText = view === 'deleted'
    ? 'No deleted service requests found.'
    : (requests.length === 0 ? 'No service requests found.' : 'No requests match the selected filters.')

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <ToastStack toasts={toasts} onDismiss={dismissToast} />
      <ConfirmDialog
        open={!!confirm}
        title={confirm?.action === 'delete' ? 'Delete selected requests?' : 'Restore selected requests?'}
        description={
          confirm?.action === 'delete'
            ? `Delete ${selectedIds.size} requests? They will be soft-deleted and can be restored later.`
            : `Restore ${selectedIds.size} requests? They will be visible again in all views and reports.`
        }
        confirmLabel={confirm?.action === 'delete' ? 'Delete' : 'Restore'}
        destructive={confirm?.action === 'delete'}
        onConfirm={() => bulkAction.mutate()}
        onCancel={() => setConfirm(null)}
      />
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          <div className="flex items-center gap-2">
            {view === 'active' && (
              <Link
                to="/dashboard/service-requests/new"
                className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <Plus className="h-4 w-4" />
                New Request
              </Link>
            )}
            {isAdmin && view === 'active' && (
              <button
                onClick={() => setView('deleted')}
                className="inline-flex items-center gap-2 rounded-md border border-border bg-background px-4 py-2 text-sm font-medium transition hover:bg-muted"
              >
                <Trash2 className="h-4 w-4" />
                View Deleted Items
              </button>
            )}
            {isAdmin && view === 'deleted' && (
              <button
                onClick={() => { setView('active'); exitSelection() }}
                className="inline-flex items-center gap-2 rounded-md border border-border bg-background px-4 py-2 text-sm font-medium transition hover:bg-muted"
              >
                <RotateCcw className="h-4 w-4" />
                Back to Active
              </button>
            )}
            {isAdmin && (
              <button
                onClick={() => setSelectionMode((v) => !v)}
                className="inline-flex items-center gap-2 rounded-md border border-border bg-background px-4 py-2 text-sm font-medium transition hover:bg-muted"
              >
                {selectionMode ? (
                  <><XSquare className="h-4 w-4" /> Cancel</>
                ) : (
                  <><CheckSquare className="h-4 w-4" /> Select</>
                )}
              </button>
            )}
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

        {selectionMode && isAdmin && (
          <BulkActionToolbar
            selectedCount={selectedIds.size}
            view={view}
            onCancel={exitSelection}
            onDelete={() => setConfirm({ open: true, action: 'delete' })}
            onRestore={() => setConfirm({ open: true, action: 'restore' })}
          />
        )}

        <DataTable<ServiceRequest>
          caption={view === 'deleted' ? 'Deleted service requests' : 'List of service requests'}
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
          onRowClick={!selectionMode ? (row) => navigate(`/dashboard/service-requests/${row.id}`) : undefined}
          selectable={selectionMode}
          selectedIds={selectedIds}
          onSelectionChange={setSelectedIds}
          emptyText={emptyText}
        />
      </div>
    </div>
  )
}
