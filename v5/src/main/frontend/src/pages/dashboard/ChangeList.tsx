import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Calendar, Plus, Trash2, RotateCcw, CheckSquare, XSquare } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { DataTable } from '../../components/ui/DataTable'
import { StatusBadge, formatStatusLabel } from '../../components/ui/StatusBadge'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { formatDate } from '../../lib/date'
import { BulkActionToolbar } from '../../components/ui/BulkActionToolbar'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { ToastStack, type ToastItem } from '../../components/ui/Toast'

export interface Change {
  id: string
  number: number
  title: string
  changeType: string
  risk: string
  status: string
  plannedStart: string | null
  plannedEnd: string | null
}

const statusOptions = ['', 'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'ROLLED_BACK', 'REJECTED', 'CANCELLED']

export function ChangeList() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const { currentUser } = useAuth()
  const isAdmin = currentUser?.roles.some((r) => ['ADMIN', 'SUPER_ADMIN'].includes(r)) ?? false

  const [filter, setFilter] = useState('')
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

  const query = useQuery<Change[]>({
    queryKey: ['changes', filter, view],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/changes?showDeleted=${view === 'deleted'}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      return filter ? data.filter((c: Change) => c.status === filter) : data
    },
  })

  const bulkAction = useMutation<{ action: 'delete' | 'restore' }, Error, void>({
    mutationFn: async () => {
      const ids = Array.from(selectedIds)
      const action = confirm?.action ?? 'delete'
      const url = `/api/v1/bulk/change-requests/${action === 'delete' ? 'soft-delete' : 'restore'}`
      const res = await fetchWithToken(instance, account!, url, {
        method: 'POST',
        body: JSON.stringify(ids),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return { action }
    },
    onSuccess: ({ action }) => {
      exitSelection()
      queryClient.invalidateQueries({ queryKey: ['changes'] })
      pushToast('success', action === 'delete' ? 'Selected changes deleted.' : 'Selected changes restored.')
      setConfirm(null)
    },
    onError: (err) => {
      pushToast('error', `Bulk ${confirm?.action} failed: ${err.message}`)
      setConfirm(null)
    },
  })

  if (query.isLoading) return <Loading />
  if (query.error) return <ErrorFallback error={query.error} message="Could not load changes." onRetry={() => query.refetch()} />

  const title = view === 'deleted' ? 'Deleted Change Requests' : 'Change Requests'
  const emptyText = view === 'deleted' ? 'No deleted change requests found.' : 'No change requests found.'

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <ToastStack toasts={toasts} onDismiss={dismissToast} />
      <ConfirmDialog
        open={!!confirm}
        title={confirm?.action === 'delete' ? 'Delete selected change requests?' : 'Restore selected change requests?'}
        description={
          confirm?.action === 'delete'
            ? `Delete ${selectedIds.size} change requests? They will be soft-deleted and can be restored later.`
            : `Restore ${selectedIds.size} change requests? They will be visible again in all views and reports.`
        }
        confirmLabel={confirm?.action === 'delete' ? 'Delete' : 'Restore'}
        destructive={confirm?.action === 'delete'}
        onConfirm={() => bulkAction.mutate()}
        onCancel={() => setConfirm(null)}
      />
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          <div className="flex items-center gap-2">
            {view === 'active' && (
              <>
                <Link
                  to="/dashboard/changes/calendar"
                  className="inline-flex items-center gap-2 rounded-md border border-border bg-background px-4 py-2 text-sm font-medium transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <Calendar className="h-4 w-4" />
                  Calendar
                </Link>
                <Link
                  to="/dashboard/changes/new"
                  className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <Plus className="h-4 w-4" />
                  New Change
                </Link>
              </>
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

        {view === 'active' && (
          <div className="flex items-center gap-3">
            <label htmlFor="status-filter" className="text-sm font-medium">Filter</label>
            <select
              id="status-filter"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            >
              <option value="">All</option>
              {statusOptions.slice(1).map((s) => <option key={s} value={s}>{formatStatusLabel(s)}</option>)}
            </select>
          </div>
        )}

        {selectionMode && isAdmin && (
          <BulkActionToolbar
            selectedCount={selectedIds.size}
            view={view}
            onCancel={exitSelection}
            onDelete={() => setConfirm({ open: true, action: 'delete' })}
            onRestore={() => setConfirm({ open: true, action: 'restore' })}
          />
        )}

        <DataTable<Change>
          caption={view === 'deleted' ? 'Deleted change requests' : 'Change requests'}
          columns={[
            { key: 'number', header: '#' },
            { key: 'title', header: 'Title' },
            { key: 'changeType', header: 'Type' },
            { key: 'risk', header: 'Risk' },
            { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
            { key: 'planned', header: 'Planned', render: (row) => `${formatDate(row.plannedStart)} - ${formatDate(row.plannedEnd)}` },
          ]}
          data={query.data ?? []}
          getRowKey={(row) => row.id}
          onRowClick={!selectionMode ? (row) => navigate(`/dashboard/changes/${row.id}`) : undefined}
          selectable={selectionMode}
          selectedIds={selectedIds}
          onSelectionChange={setSelectedIds}
          emptyText={emptyText}
        />
      </div>
    </div>
  )
}
