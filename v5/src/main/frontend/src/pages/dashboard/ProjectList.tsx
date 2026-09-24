import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { ArrowLeft, FolderKanban, Plus } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { DataTable } from '../../components/ui/DataTable'
import { EntityForm } from '../../components/ui/EntityForm'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { FormDrawer } from '../../components/ui/FormDrawer'
import { Loading } from '../../components/ui/Loading'
import { useAuth } from '../../auth/AuthProvider'
import { useSmartBack } from '../../lib/useSmartBack'

interface ProjectResponse {
  id: string
  key: string
  name: string
  description: string
  leadName: string
  status: string
}


export function ProjectList() {
  const smartBack = useSmartBack('/dashboard')
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { currentUser } = useAuth()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [form, setForm] = useState<Record<string, string>>({
    key: '',
    name: '',
    description: '',
    leadId: '',
  })
  const [formError, setFormError] = useState<string | null>(null)

  const query = useQuery<ProjectResponse[]>({
    queryKey: ['projects'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/projects')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const usersQuery = useQuery<{ id: string; displayName: string }[]>({
    queryKey: ['users'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/users')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const leadOptions = (usersQuery.data ?? []).map((u) => ({ value: u.id, label: u.displayName }))

  const createFields = [
    { name: 'key', label: 'Key', type: 'text' as const, required: true },
    { name: 'name', label: 'Name', type: 'text' as const, required: true },
    { name: 'description', label: 'Description', type: 'textarea' as const },
    { name: 'leadId', label: 'Lead', type: 'select' as const, options: leadOptions },
  ]

  const createMutation = useMutation<ProjectResponse, Error, Record<string, string | null>>({
    mutationFn: async (payload) => {
      const res = await fetchWithToken(instance, account!, '/api/v1/projects', {
        method: 'POST',
        body: JSON.stringify(payload),
      })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: (data) => {
      setFormError(null)
      setDrawerOpen(false)
      setForm({ key: '', name: '', description: '', leadId: '' })
      queryClient.invalidateQueries({ queryKey: ['projects'] })
      navigate(`/dashboard/projects/${data.id}`)
    },
    onError: (error) => setFormError(error.message),
  })

  const canCreate = currentUser?.roles?.some((r) =>
    ['ADMIN', 'SUPER_ADMIN', 'TEAM_LEAD'].includes(r)
  )

  if (query.isLoading) return <Loading />
  if (query.error) return <ErrorFallback error={query.error} message="Could not load projects." onRetry={() => query.refetch()} />

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setFormError(null)
    createMutation.mutate({
      key: form.key,
      name: form.name,
      description: form.description || null,
      leadId: form.leadId || null,
    })
  }

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
          <h1 className="text-2xl font-semibold tracking-tight">Projects</h1>
          {canCreate && (
            <button
              onClick={() => setDrawerOpen(true)}
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <Plus className="h-4 w-4" />
              New Project
            </button>
          )}
        </div>

        <DataTable<ProjectResponse>
          caption="Projects"
          columns={[
            { key: 'key', header: 'Key' },
            { key: 'name', header: 'Name' },
            { key: 'leadName', header: 'Lead' },
            { key: 'status', header: 'Status' },
            {
              key: 'actions',
              header: 'Actions',
              render: (row) => (
                <Link to={`/dashboard/projects/${row.id}`} className="inline-flex items-center gap-1 text-sm font-medium text-primary underline-offset-4 hover:underline">
                  <FolderKanban className="h-4 w-4" /> View
                </Link>
              ),
            },
          ]}
          data={query.data ?? []}
          getRowKey={(row) => row.id}
          emptyText="No projects."
        />

        <FormDrawer open={drawerOpen} title="New Project" dirty={form.key !== '' || form.name !== '' || form.description !== '' || form.leadId !== ''} onClose={() => setDrawerOpen(false)}>
          <EntityForm
            fields={createFields}
            values={form}
            onChange={(name, value) => setForm({ ...form, [name]: value })}
            onSubmit={handleSubmit}
            submitLabel="Create"
            pending={createMutation.isPending}
          />
          {formError && <p className="mt-4 text-sm text-destructive">{formError}</p>}
        </FormDrawer>
      </div>
    </div>
  )
}
