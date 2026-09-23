import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMsal } from '@azure/msal-react'
import { ArrowLeft, Plus, Trash2, RotateCcw, CheckSquare, XSquare } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { DataTable } from '../../components/ui/DataTable'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { FormDrawer } from '../../components/ui/FormDrawer'
import { EntityForm, type Field } from '../../components/ui/EntityForm'
import { BulkActionToolbar } from '../../components/ui/BulkActionToolbar'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { ToastStack, type ToastItem } from '../../components/ui/Toast'
import { useSmartBack } from '../../lib/useSmartBack'

export interface Problem {
  id: string
  number: number
  title: string
  status: string
  rootCause: string | null
}

const createFields: Field[] = [
  { name: 'title', label: 'Title', type: 'text', required: true },
  { name: 'description', label: 'Description', type: 'textarea' },
]

export function ProblemList() {
  const smartBack = useSmartBack('/dashboard')
  const navigate = useNavigate()
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()
  const { currentUser } = useAuth()
  const isAdmin = currentUser?.roles.some((r) => ['ADMIN', 'SUPER_ADMIN'].includes(r)) ?? false

  const [view, setView] = useState<'active' | 'deleted'>('active')
  const [selectionMode, setSelectionMode] = useState(false)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const [form, setForm] = useState({ title: '', description: '' })
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

  const query = useQuery<Problem[]>({
    queryKey: ['problems', view],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/problems?showDeleted=${view === 'deleted'}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const createMutation = useMutation<Problem, Error, typeof form>({
    mutationFn: async (payload) => {
      const res = await fetchWithToken(instance, account!, '/api/v1/problems', {
        method: 'POST',
        body: JSON.stringify(payload),
      })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['problems'] })
      setCreateError(null)
      setForm({ title: '', description: '' })
      setDrawerOpen(false)
    },
    onError: (error) => setCreateError(error.message),
  })

  const bulkAction = useMutation<{ action: 'delete' | 'restore' }, Error, void>({
    mutationFn: async () => {
      const ids = Array.from(selectedIds)
      const action = confirm?.action ?? 'delete'
      const url = `/api/v1/bulk/problems/${action === 'delete' ? 'soft-delete' : 'restore'}`
      const res = await fetchWithToken(instance, account!, url, {
        method: 'POST',
        body: JSON.stringify(ids),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return { action }
    },
    onSuccess: ({ action }) => {
      exitSelection()
      queryClient.invalidateQueries({ queryKey: ['problems'] })
      pushToast('success', action === 'delete' ? 'Selected problems deleted.' : 'Selected problems restored.')
      setConfirm(null)
    },
    onError: (err) => {
      pushToast('error', `Bulk ${confirm?.action} failed: ${err.message}`)
      setConfirm(null)
    },
  })

  if (query.isLoading) return <Loading />
  if (query.error) return <ErrorFallback error={query.error} message="Could not load problems." onRetry={() => query.refetch()} />

  const title = view === 'deleted' ? 'Deleted Problems' : 'Problems'
  const emptyText = view === 'deleted' ? 'No deleted problems found.' : 'No problems found.'

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <ToastStack toasts={toasts} onDismiss={dismissToast} />
      <ConfirmDialog
        open={!!confirm}
        title={confirm?.action === 'delete' ? 'Delete selected problems?' : 'Restore selected problems?'}
        description={
          confirm?.action === 'delete'
            ? `Delete ${selectedIds.size} problems? They will be soft-deleted and can be restored later.`
            : `Restore ${selectedIds.size} problems? They will be visible again in all views and reports.`
        }
        confirmLabel={confirm?.action === 'delete' ? 'Delete' : 'Restore'}
        destructive={confirm?.action === 'delete'}
        onConfirm={() => bulkAction.mutate()}
        onCancel={() => setConfirm(null)}
      />
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex items-center justify-between">
          <button
            onClick={smartBack}
            className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </button>
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          <div className="flex items-center gap-2">
            {view === 'active' && (
              <button
                onClick={() => setDrawerOpen(true)}
                className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <Plus className="h-4 w-4" />
                New Problem
              </button>
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

        {selectionMode && isAdmin && (
          <BulkActionToolbar
            selectedCount={selectedIds.size}
            view={view}
            onCancel={exitSelection}
            onDelete={() => setConfirm({ open: true, action: 'delete' })}
            onRestore={() => setConfirm({ open: true, action: 'restore' })}
          />
        )}

        <DataTable<Problem>
          caption={view === 'deleted' ? 'Deleted problems' : 'List of problems'}
          columns={[
            { key: 'number', header: 'Number' },
            { key: 'title', header: 'Title' },
            { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
            { key: 'rootCause', header: 'Root Cause', render: (row) => row.rootCause ?? '—' },
          ]}
          data={query.data ?? []}
          getRowKey={(row) => row.id}
          onRowClick={!selectionMode ? (row) => navigate(`/dashboard/problems/${row.id}`) : undefined}
          selectable={selectionMode}
          selectedIds={selectedIds}
          onSelectionChange={setSelectedIds}
          emptyText={emptyText}
        />
      </div>

      <FormDrawer open={drawerOpen} title="New Problem" dirty={form.title !== '' || form.description !== ''} onClose={() => { setDrawerOpen(false); setCreateError(null) }}>
        <EntityForm
          fields={createFields}
          values={form}
          onChange={(name, value) => setForm({ ...form, [name]: value })}
          onSubmit={(e) => {
            e.preventDefault()
            createMutation.mutate(form)
          }}
          submitLabel={createMutation.isPending ? 'Creating…' : 'Create Problem'}
          pending={createMutation.isPending}
        >
          {createError && <p className="text-sm text-destructive">{createError}</p>}
        </EntityForm>
      </FormDrawer>
    </div>
  )
}
