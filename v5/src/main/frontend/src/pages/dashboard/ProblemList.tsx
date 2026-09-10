import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useMsal } from '@azure/msal-react'
import { Plus } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { DataTable } from '../../components/ui/DataTable'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { FormDrawer } from '../../components/ui/FormDrawer'
import { EntityForm, type Field } from '../../components/ui/EntityForm'
import { BulkActionToolbar } from '../../components/ui/BulkActionToolbar'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { ToastStack, type ToastItem } from '../../components/ui/Toast'

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
  const navigate = useNavigate()
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const [form, setForm] = useState({ title: '', description: '' })
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

  const query = useQuery<Problem[]>({
    queryKey: ['problems', showDeleted],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/problems?showDeleted=${showDeleted}`)
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

  const bulkAction = useMutation<void, Error, void>({
    mutationFn: async () => {
      const ids = Array.from(selectedIds)
      const action = confirm?.action ?? 'delete'
      const url = `/api/v1/bulk/problems/${action === 'delete' ? 'soft-delete' : 'restore'}`
      const res = await fetchWithToken(instance, account!, url, {
        method: 'POST',
        body: JSON.stringify(ids),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
    },
    onSuccess: () => {
      setSelectedIds(new Set())
      queryClient.invalidateQueries({ queryKey: ['problems'] })
      pushToast('success', confirm?.action === 'delete' ? 'Selected problems deleted.' : 'Selected problems restored.')
      setConfirm(null)
    },
    onError: (err) => {
      pushToast('error', `Bulk ${confirm?.action} failed: ${err.message}`)
      setConfirm(null)
    },
  })

  if (query.isLoading) return <Loading />
  if (query.error) return <ErrorFallback error={query.error} message="Could not load problems." onRetry={() => query.refetch()} />

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <ToastStack toasts={toasts} onDismiss={dismissToast} />
      <ConfirmDialog
        open={!!confirm}
        title={confirm?.action === 'delete' ? 'Delete selected problems?' : 'Restore selected problems?'}
        description={
          confirm?.action === 'delete'
            ? `Delete ${selectedIds.size} problems? This can be undone via Restore.`
            : `Restore ${selectedIds.size} problems? They will be visible again in all views and reports.`
        }
        confirmLabel={confirm?.action === 'delete' ? 'Delete' : 'Restore'}
        destructive={confirm?.action === 'delete'}
        onConfirm={() => bulkAction.mutate()}
        onCancel={() => setConfirm(null)}
      />
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold tracking-tight">Problems</h1>
          <button
            onClick={() => setDrawerOpen(true)}
            className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <Plus className="h-4 w-4" />
            New Problem
          </button>
        </div>

        <BulkActionToolbar
          selectedCount={selectedIds.size}
          showDeleted={showDeleted}
          onToggleShowDeleted={() => { setShowDeleted((v) => !v); setSelectedIds(new Set()) }}
          onDelete={() => setConfirm({ open: true, action: 'delete' })}
          onRestore={() => setConfirm({ open: true, action: 'restore' })}
        />

        <DataTable<Problem>
          caption="List of problems"
          columns={[
            { key: 'number', header: 'Number' },
            { key: 'title', header: 'Title' },
            { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
            { key: 'rootCause', header: 'Root Cause', render: (row) => row.rootCause ?? '—' },
          ]}
          data={query.data ?? []}
          getRowKey={(row) => row.id}
          onRowClick={(row) => navigate(`/dashboard/problems/${row.id}`)}
          selectable
          selectedIds={selectedIds}
          onSelectionChange={setSelectedIds}
          emptyText="No problems found."
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
