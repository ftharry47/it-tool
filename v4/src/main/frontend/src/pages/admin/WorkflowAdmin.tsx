import { useMsal } from '@azure/msal-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { fetchWithToken } from '../../api/client'
import { DataTable } from '../../components/ui/DataTable'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { Loading } from '../../components/ui/Loading'
import { ToastStack, type ToastItem } from '../../components/ui/Toast'

interface WorkflowResponse {
  id: string
  name: string
  description: string
  projectId: string | null
  statuses: WorkflowStatusResponse[]
  transitions: WorkflowTransitionResponse[]
}

interface WorkflowStatusResponse {
  id: string
  name: string
  category: 'BACKLOG' | 'TODO' | 'IN_PROGRESS' | 'DONE'
  displayOrder: number
  terminal: boolean
}

interface WorkflowTransitionResponse {
  id: string
  fromStatusId: string
  fromStatusName: string
  toStatusId: string
  toStatusName: string
  screen: string | null
}

interface ProjectResponse {
  id: string
  key: string
  name: string
}

const CATEGORIES = ['BACKLOG', 'TODO', 'IN_PROGRESS', 'DONE'] as const

export function WorkflowAdmin() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()

  const [selectedWorkflow, setSelectedWorkflow] = useState<string | null>(null)

  const [workflowName, setWorkflowName] = useState('')
  const [workflowDesc, setWorkflowDesc] = useState('')
  const [workflowProject, setWorkflowProject] = useState('')

  const [statusName, setStatusName] = useState('')
  const [statusCategory, setStatusCategory] = useState<'BACKLOG' | 'TODO' | 'IN_PROGRESS' | 'DONE' | ''>('')
  const [statusOrder, setStatusOrder] = useState('0')
  const [statusTerminal, setStatusTerminal] = useState(false)

  const [fromStatus, setFromStatus] = useState('')
  const [toStatus, setToStatus] = useState('')
  const [transitionScreen, setTransitionScreen] = useState('')

  const [toasts, setToasts] = useState<ToastItem[]>([])
  const [confirmSwitch, setConfirmSwitch] = useState<string | null>(null)
  const [pendingStatusRemoval, setPendingStatusRemoval] = useState<string | null>(null)
  const [pendingTransitionRemoval, setPendingTransitionRemoval] = useState<string | null>(null)

  const hasPendingInputs =
    statusName !== '' ||
    statusCategory !== '' ||
    statusOrder !== '0' ||
    statusTerminal ||
    fromStatus !== '' ||
    toStatus !== '' ||
    transitionScreen !== ''

  const selectWorkflow = (id: string) => {
    if (id !== selectedWorkflow && hasPendingInputs) setConfirmSwitch(id)
    else setSelectedWorkflow(id)
  }

  const pushToast = (type: ToastItem['type'], message: string) => {
    setToasts((prev) => [...prev, { id: crypto.randomUUID(), type, message }])
  }
  const dismissToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }

  const projectsQuery = useQuery<ProjectResponse[]>({
    queryKey: ['projects'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/projects')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const workflowsQuery = useQuery<WorkflowResponse[]>({
    queryKey: ['workflows'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/workflows')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const createWorkflowMutation = useMutation<WorkflowResponse, Error>({
    mutationFn: async () => {
      const body: Record<string, unknown> = { name: workflowName, description: workflowDesc }
      if (workflowProject) body.projectId = workflowProject
      const res = await fetchWithToken(instance, account!, '/api/v1/workflows', { method: 'POST', body: JSON.stringify(body) })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      setWorkflowName('')
      setWorkflowDesc('')
      setWorkflowProject('')
      queryClient.invalidateQueries({ queryKey: ['workflows'] })
      pushToast('success', 'Workflow created')
    },
    onError: (error) => {
      console.error('Workflow create failed:', error)
      pushToast('error', 'Could not create the workflow. Please try again.')
    },
  })

  const addStatusMutation = useMutation<WorkflowStatusResponse, Error>({
    mutationFn: async () => {
      const body = {
        name: statusName,
        category: statusCategory,
        displayOrder: Number(statusOrder),
        terminal: statusTerminal,
      }
      const res = await fetchWithToken(instance, account!, `/api/v1/workflows/${selectedWorkflow}/statuses`, { method: 'POST', body: JSON.stringify(body) })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      setStatusName('')
      setStatusCategory('')
      setStatusOrder('0')
      setStatusTerminal(false)
      queryClient.invalidateQueries({ queryKey: ['workflows'] })
      pushToast('success', 'Status added')
    },
    onError: (error) => {
      console.error('Status add failed:', error)
      pushToast('error', 'Could not add the status. Please try again.')
    },
  })

  const removeStatusMutation = useMutation<void, Error, string>({
    mutationFn: async (id) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/workflows/${selectedWorkflow}/statuses/${id}`, { method: 'DELETE' })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workflows'] })
      pushToast('success', 'Status removed')
    },
    onError: (error) => {
      console.error('Status remove failed:', error)
      pushToast('error', 'Could not remove the status. It may be in use by existing transitions.')
    },
  })

  const addTransitionMutation = useMutation<WorkflowTransitionResponse, Error>({
    mutationFn: async () => {
      const body: Record<string, unknown> = { fromStatusId: fromStatus, toStatusId: toStatus }
      if (transitionScreen) body.screen = transitionScreen
      const res = await fetchWithToken(instance, account!, `/api/v1/workflows/${selectedWorkflow}/transitions`, { method: 'POST', body: JSON.stringify(body) })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      setFromStatus('')
      setToStatus('')
      setTransitionScreen('')
      queryClient.invalidateQueries({ queryKey: ['workflows'] })
      pushToast('success', 'Transition added')
    },
    onError: (error) => {
      console.error('Transition add failed:', error)
      pushToast('error', 'Could not add the transition. Please try again.')
    },
  })

  const removeTransitionMutation = useMutation<void, Error, string>({
    mutationFn: async (id) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/workflows/${selectedWorkflow}/transitions/${id}`, { method: 'DELETE' })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workflows'] })
      pushToast('success', 'Transition removed')
    },
    onError: (error) => {
      console.error('Transition remove failed:', error)
      pushToast('error', 'Could not remove the transition. Please try again.')
    },
  })

  if (workflowsQuery.isLoading || projectsQuery.isLoading) return <Loading />
  if (workflowsQuery.error) return <ErrorFallback error={workflowsQuery.error} message="Could not load workflows." onRetry={() => workflowsQuery.refetch()} />

  const selected = workflowsQuery.data?.find((w) => w.id === selectedWorkflow)

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <ToastStack toasts={toasts} onDismiss={dismissToast} />
      <ConfirmDialog
        open={confirmSwitch !== null}
        title="Discard unsaved changes?"
        description="You have unsaved status or transition inputs that will be lost if you switch workflows."
        confirmLabel="Discard"
        destructive
        onConfirm={() => {
          const id = confirmSwitch
          setConfirmSwitch(null)
          if (id) setSelectedWorkflow(id)
        }}
        onCancel={() => setConfirmSwitch(null)}
      />
      <ConfirmDialog
        open={pendingStatusRemoval !== null}
        title="Remove status?"
        description="This status will be removed from the workflow. It may fail if still used by transitions."
        confirmLabel="Remove"
        destructive
        onConfirm={() => {
          if (pendingStatusRemoval) removeStatusMutation.mutate(pendingStatusRemoval)
          setPendingStatusRemoval(null)
        }}
        onCancel={() => setPendingStatusRemoval(null)}
      />
      <ConfirmDialog
        open={pendingTransitionRemoval !== null}
        title="Remove transition?"
        description="This transition will be removed from the workflow."
        confirmLabel="Remove"
        destructive
        onConfirm={() => {
          if (pendingTransitionRemoval) removeTransitionMutation.mutate(pendingTransitionRemoval)
          setPendingTransitionRemoval(null)
        }}
        onCancel={() => setPendingTransitionRemoval(null)}
      />
      <div className="mx-auto max-w-5xl space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">Workflow Builder</h1>

        <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold">Create Workflow</h2>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <input value={workflowName} onChange={(e) => setWorkflowName(e.target.value)} placeholder="Name" className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
            <input value={workflowDesc} onChange={(e) => setWorkflowDesc(e.target.value)} placeholder="Description" className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
            <select value={workflowProject} onChange={(e) => setWorkflowProject(e.target.value)} className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring">
              <option value="">Global / no project</option>
              {projectsQuery.data?.map((p) => <option key={p.id} value={p.id}>{p.key} — {p.name}</option>)}
            </select>
          </div>
          <button
            onClick={() => createWorkflowMutation.mutate()}
            disabled={!workflowName || createWorkflowMutation.isPending}
            className="mt-4 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
          >
            Create
          </button>
          {createWorkflowMutation.error && <p className="mt-2 text-sm text-destructive">{createWorkflowMutation.error.message}</p>}
        </div>

        <DataTable<WorkflowResponse>
          caption="Workflows"
          columns={[
            { key: 'name', header: 'Name' },
            { key: 'description', header: 'Description' },
            {
              key: 'actions',
              header: 'Actions',
              render: (row) => (
                <button
                  onClick={() => selectWorkflow(row.id)}
                  className={`text-sm font-medium underline-offset-4 hover:underline ${selectedWorkflow === row.id ? 'text-primary' : 'text-muted-foreground'}`}
                >
                  {selectedWorkflow === row.id ? 'Editing' : 'Edit'}
                </button>
              ),
            },
          ]}
          data={workflowsQuery.data ?? []}
          getRowKey={(row) => row.id}
          emptyText="No workflows."
        />

        {selected && (
          <div className="space-y-6">
            <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
              <h2 className="mb-4 text-lg font-semibold">Statuses</h2>
              <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-5">
                <input value={statusName} onChange={(e) => setStatusName(e.target.value)} placeholder="Name" className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
                <select value={statusCategory} onChange={(e) => setStatusCategory(e.target.value as any)} className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring">
                  <option value="">Category</option>
                  {CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
                </select>
                <input type="number" value={statusOrder} onChange={(e) => setStatusOrder(e.target.value)} placeholder="Order" className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={statusTerminal} onChange={(e) => setStatusTerminal(e.target.checked)} className="h-4 w-4" />
                  Terminal
                </label>
                <button
                  onClick={() => addStatusMutation.mutate()}
                  disabled={!statusName || !statusCategory || addStatusMutation.isPending}
                  className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
                >
                  Add Status
                </button>
              </div>
              {addStatusMutation.error && <p className="mb-2 text-sm text-destructive">{addStatusMutation.error.message}</p>}
              <DataTable<WorkflowStatusResponse>
                caption="Statuses"
                columns={[
                  { key: 'name', header: 'Name' },
                  { key: 'category', header: 'Category' },
                  { key: 'displayOrder', header: 'Order' },
                  { key: 'terminal', header: 'Terminal' },
                  {
                    key: 'actions',
                    header: 'Actions',
                    render: (row) => (
                      <button onClick={() => setPendingStatusRemoval(row.id)} className="text-sm text-destructive hover:underline">Remove</button>
                    ),
                  },
                ]}
                data={selected.statuses}
                getRowKey={(row) => row.id}
                emptyText="No statuses."
              />
            </div>

            <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
              <h2 className="mb-4 text-lg font-semibold">Transitions</h2>
              <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-4">
                <select value={fromStatus} onChange={(e) => setFromStatus(e.target.value)} className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring">
                  <option value="">From status</option>
                  {selected.statuses.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
                <select value={toStatus} onChange={(e) => setToStatus(e.target.value)} className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring">
                  <option value="">To status</option>
                  {selected.statuses.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
                <input value={transitionScreen} onChange={(e) => setTransitionScreen(e.target.value)} placeholder="Screen (optional)" className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
                <button
                  onClick={() => addTransitionMutation.mutate()}
                  disabled={!fromStatus || !toStatus || addTransitionMutation.isPending}
                  className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
                >
                  Add Transition
                </button>
              </div>
              {addTransitionMutation.error && <p className="mb-2 text-sm text-destructive">{addTransitionMutation.error.message}</p>}
              <DataTable<WorkflowTransitionResponse>
                caption="Transitions"
                columns={[
                  { key: 'fromStatusName', header: 'From' },
                  { key: 'toStatusName', header: 'To' },
                  { key: 'screen', header: 'Screen' },
                  {
                    key: 'actions',
                    header: 'Actions',
                    render: (row) => (
                      <button onClick={() => setPendingTransitionRemoval(row.id)} className="text-sm text-destructive hover:underline">Remove</button>
                    ),
                  },
                ]}
                data={selected.transitions}
                getRowKey={(row) => row.id}
                emptyText="No transitions."
              />
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
