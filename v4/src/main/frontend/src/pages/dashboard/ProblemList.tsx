import { useState } from 'react'
import { Link } from 'react-router-dom'
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
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const [form, setForm] = useState({ title: '', description: '' })

  const query = useQuery<Problem[]>({
    queryKey: ['problems'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/problems')
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

  if (query.isLoading) return <Loading />
  if (query.error) return <ErrorFallback error={query.error} message="Could not load problems." onRetry={() => query.refetch()} />

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
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

        <DataTable<Problem>
          caption="List of problems"
          columns={[
            { key: 'number', header: 'Number' },
            { key: 'title', header: 'Title', render: (row) => <Link to={`/dashboard/problems/${row.id}`} className="font-medium hover:underline">{row.title}</Link> },
            { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
            { key: 'rootCause', header: 'Root Cause', render: (row) => row.rootCause ?? '—' },
          ]}
          data={query.data ?? []}
          getRowKey={(row) => row.id}
          emptyText="No problems found."
        />
      </div>

      <FormDrawer open={drawerOpen} title="New Problem" onClose={() => { setDrawerOpen(false); setCreateError(null) }}>
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
