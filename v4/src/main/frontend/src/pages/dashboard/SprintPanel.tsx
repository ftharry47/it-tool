import { useMsal } from '@azure/msal-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { fetchWithToken } from '../../api/client'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { Loading } from '../../components/ui/Loading'

interface SprintResponse {
  id: string
  projectId: string
  name: string
  goal: string
  status: 'PLANNING' | 'ACTIVE' | 'COMPLETED'
  startDate: string
  endDate: string
  completedAt: string | null
}

interface BurndownSnapshot {
  snapshotDate: string
  totalPoints: number
  remainingPoints: number
  openIssues: number
}

function toLocalInput(iso: string) {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function toIso(input: string) {
  if (!input) return ''
  return input.endsWith('Z') ? input : input + ':00Z'
}

export function SprintPanel({ projectId }: { projectId: string }) {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()

  const [name, setName] = useState('')
  const [goal, setGoal] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')

  const [completing, setCompleting] = useState<SprintResponse | null>(null)
  const [destination, setDestination] = useState<'BACKLOG' | 'NEXT_SPRINT' | ''>('')
  const [nextSprintId, setNextSprintId] = useState('')
  const [burndownId, setBurndownId] = useState<string | null>(null)

  const sprintsQuery = useQuery<SprintResponse[]>({
    queryKey: ['project-sprints', projectId],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/projects/${projectId}/sprints`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const burndownQuery = useQuery<BurndownSnapshot[]>({
    queryKey: ['sprint-burndown', burndownId],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/projects/${projectId}/sprints/${burndownId}/burndown`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!burndownId,
  })

  const createMutation = useMutation<SprintResponse, Error>({
    mutationFn: async () => {
      const body: Record<string, unknown> = { projectId, name, goal }
      if (startDate) body.startDate = toIso(startDate)
      if (endDate) body.endDate = toIso(endDate)
      const res = await fetchWithToken(instance, account!, `/api/v1/projects/${projectId}/sprints`, { method: 'POST', body: JSON.stringify(body) })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      setName('')
      setGoal('')
      setStartDate('')
      setEndDate('')
      queryClient.invalidateQueries({ queryKey: ['project-sprints', projectId] })
    },
  })

  const startMutation = useMutation<SprintResponse, Error, string>({
    mutationFn: async (id) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/projects/${projectId}/sprints/${id}/start`, { method: 'POST' })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['project-sprints', projectId] }),
  })

  const completeMutation = useMutation<SprintResponse, Error>({
    mutationFn: async () => {
      if (!completing) throw new Error('No sprint selected')
      const body: { destination: string; nextSprintId?: string } = { destination }
      if (destination === 'NEXT_SPRINT' && nextSprintId) {
        body.nextSprintId = nextSprintId
      }
      const res = await fetchWithToken(instance, account!, `/api/v1/projects/${projectId}/sprints/${completing.id}/complete`, { method: 'POST', body: JSON.stringify(body) })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      setCompleting(null)
      setDestination('')
      setNextSprintId('')
      queryClient.invalidateQueries({ queryKey: ['project-sprints', projectId] })
    },
  })

  if (sprintsQuery.isLoading) return <Loading />
  if (sprintsQuery.error) return <ErrorFallback error={sprintsQuery.error} message="Could not load sprints." onRetry={() => sprintsQuery.refetch()} />

  const sprints = sprintsQuery.data ?? []
  const otherSprints = sprints.filter((s) => s.status !== 'COMPLETED' && s.id !== completing?.id)

  return (
    <div className="space-y-6">
      <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
        <h2 className="mb-4 text-lg font-semibold">Create Sprint</h2>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Name" className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
          <input value={goal} onChange={(e) => setGoal(e.target.value)} placeholder="Goal" className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
          <input type="datetime-local" value={startDate} onChange={(e) => setStartDate(e.target.value)} placeholder="Start" className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
          <input type="datetime-local" value={endDate} onChange={(e) => setEndDate(e.target.value)} placeholder="End" className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
        </div>
        <button
          onClick={() => createMutation.mutate()}
          disabled={!name || createMutation.isPending}
          className="mt-4 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
        >
          Create
        </button>
        {createMutation.error && <p className="mt-2 text-sm text-destructive">{createMutation.error.message}</p>}
      </div>

      <div className="space-y-3">
        {sprints.map((sprint) => (
          <div key={sprint.id} className="rounded-xl border border-border bg-card p-4 shadow-sm">
            <div className="flex items-start justify-between">
              <div>
                <h3 className="text-base font-semibold">{sprint.name}</h3>
                <p className="text-sm text-muted-foreground">{sprint.goal}</p>
                <p className="text-xs text-muted-foreground">Status: {sprint.status} · {sprint.startDate ? toLocalInput(sprint.startDate) : '—'} to {sprint.endDate ? toLocalInput(sprint.endDate) : '—'}</p>
              </div>
              <div className="flex gap-2">
                {sprint.status === 'PLANNING' && (
                  <button onClick={() => startMutation.mutate(sprint.id)} disabled={startMutation.isPending} className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50">Start</button>
                )}
                {sprint.status === 'ACTIVE' && (
                  <button onClick={() => setCompleting(sprint)} className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground transition hover:bg-primary/90">Complete</button>
                )}
                {sprint.status === 'COMPLETED' && (
                  <button onClick={() => setBurndownId(sprint.id)} className="rounded-md border border-border px-3 py-1.5 text-sm font-medium transition hover:bg-muted">Burndown</button>
                )}
              </div>
            </div>
            {startMutation.error && startMutation.variables === sprint.id && <p className="mt-2 text-sm text-destructive">{startMutation.error.message}</p>}
          </div>
        ))}
      </div>

      {completing && (
        <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h3 className="mb-2 text-lg font-semibold">Complete {completing.name}</h3>
          <p className="mb-4 text-sm text-muted-foreground">Choose where to move any remaining unfinished issues. A destination must be selected explicitly.</p>

          <div className="mb-4 space-y-3">
            <select
              value={destination}
              onChange={(e) => { setDestination(e.target.value as any); setNextSprintId('') }}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            >
              <option value="" disabled>— Select destination —</option>
              <option value="BACKLOG">Backlog</option>
              <option value="NEXT_SPRINT">Next sprint</option>
            </select>

            {destination === 'NEXT_SPRINT' && (
              <select
                value={nextSprintId}
                onChange={(e) => setNextSprintId(e.target.value)}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              >
                <option value="" disabled>— Select next sprint —</option>
                {otherSprints.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            )}
          </div>

          <button
            onClick={() => completeMutation.mutate()}
            disabled={!destination || (destination === 'NEXT_SPRINT' && !nextSprintId) || completeMutation.isPending}
            className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
          >
            Confirm Complete
          </button>
          {completeMutation.error && <p className="mt-2 text-sm text-destructive">{completeMutation.error.message}</p>}
        </div>
      )}

      {burndownId && (
        <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h3 className="mb-4 text-lg font-semibold">Burndown</h3>
          {burndownQuery.isLoading && <Loading />}
          {burndownQuery.error && <ErrorFallback error={burndownQuery.error} message="Could not load burndown." onRetry={() => burndownQuery.refetch()} />}
          {burndownQuery.data && burndownQuery.data.length === 0 && <p className="text-sm text-muted-foreground">No burndown snapshots yet.</p>}
          {burndownQuery.data && burndownQuery.data.length > 0 && (
            <div className="h-80">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={burndownQuery.data.map((s) => ({ date: new Date(s.snapshotDate).toLocaleDateString(), remaining: s.remainingPoints, total: s.totalPoints }))}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" />
                  <YAxis />
                  <Tooltip />
                  <Line type="monotone" dataKey="remaining" stroke="#3b82f6" strokeWidth={2} dot={false} name="Remaining" />
                  <Line type="monotone" dataKey="total" stroke="#10b981" strokeWidth={2} dot={false} name="Total" />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
          <button onClick={() => setBurndownId(null)} className="mt-4 rounded-md border border-border px-3 py-1.5 text-sm transition hover:bg-muted">Close</button>
        </div>
      )}
    </div>
  )
}
