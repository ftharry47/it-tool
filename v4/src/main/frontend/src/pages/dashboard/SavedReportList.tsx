import { useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Play, Plus, Save, Trash2 } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { DataTable } from '../../components/ui/DataTable'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { Loading } from '../../components/ui/Loading'

interface SavedReport {
  id: string
  name: string
  entity: string
  filters: string
  groupBy: string
  dateRange: string
}

interface ReportMetadata {
  entities: string[]
  fieldsByEntity: Record<string, string[]>
  operators: string[]
  dateFieldByEntity: Record<string, string>
}

interface QueryFilter {
  field: string
  op: string
  value: string
}

interface QueryResult {
  rows: { group: string; count: number }[]
  groupBy: string | null
}

function toIso(input: string) {
  if (!input) return ''
  return input.endsWith('Z') ? input : input + ':00Z'
}

function toLocalInput(iso: string) {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function SavedReportList() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()

  const [editing, setEditing] = useState<SavedReport | null>(null)
  const [name, setName] = useState('')
  const [entity, setEntity] = useState('')
  const [groupBy, setGroupBy] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [filters, setFilters] = useState<QueryFilter[]>([{ field: '', op: '', value: '' }])
  const [result, setResult] = useState<QueryResult | null>(null)

  const listQuery = useQuery<SavedReport[]>({
    queryKey: ['saved-reports'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/reports/saved')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const metaQuery = useQuery<ReportMetadata>({
    queryKey: ['reports-metadata'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/reports/metadata')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const availableFields = entity ? (metaQuery.data?.fieldsByEntity[entity] ?? []) : []

  const saveMutation = useMutation<SavedReport, Error>({
    mutationFn: async () => {
      const body = {
        name,
        entity,
        groupBy,
        filters: filters.filter((f) => f.field && f.op),
        dateRange: from || to ? { from: from ? toIso(from) : null, to: to ? toIso(to) : null } : null,
      }
      const url = editing ? `/api/v1/reports/saved/${editing.id}` : '/api/v1/reports/saved'
      const method = editing ? 'PATCH' : 'POST'
      const res = await fetchWithToken(instance, account!, url, { method, body: JSON.stringify(body) })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['saved-reports'] })
      resetForm()
    },
  })

  const runMutation = useMutation<QueryResult, Error, SavedReport>({
    mutationFn: async (report) => {
      const f = parseJson(report.filters, [])
      const dr = parseJson(report.dateRange, null)
      const body: Record<string, unknown> = { entity: report.entity, filters: f }
      if (report.groupBy) body.groupBy = report.groupBy
      if (dr) body.dateRange = dr
      const res = await fetchWithToken(instance, account!, '/api/v1/reports/query', { method: 'POST', body: JSON.stringify(body) })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: (data) => setResult(data),
  })

  const deleteMutation = useMutation<void, Error, string>({
    mutationFn: async (id) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/reports/saved/${id}`, { method: 'DELETE' })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['saved-reports'] }),
  })

  function parseJson<T>(value: string, fallback: T) {
    try {
      return JSON.parse(value) as T
    } catch {
      return fallback
    }
  }

  function resetForm() {
    setEditing(null)
    setName('')
    setEntity('')
    setGroupBy('')
    setFrom('')
    setTo('')
    setFilters([{ field: '', op: '', value: '' }])
    setResult(null)
  }

  function loadEdit(report: SavedReport) {
    setEditing(report)
    setName(report.name)
    setEntity(report.entity)
    setGroupBy(report.groupBy ?? '')
    const dr = parseJson<{ from: string; to: string } | null>(report.dateRange, null)
    setFrom(dr?.from ? toLocalInput(dr.from) : '')
    setTo(dr?.to ? toLocalInput(dr.to) : '')
    const f = parseJson<QueryFilter[]>(report.filters, [])
    setFilters(f.length ? f : [{ field: '', op: '', value: '' }])
    setResult(null)
  }

  const updateFilter = (i: number, patch: Partial<QueryFilter>) => {
    const next = [...filters]
    next[i] = { ...next[i], ...patch }
    setFilters(next)
  }

  const addFilter = () => setFilters([...filters, { field: '', op: '', value: '' }])
  const removeFilter = (i: number) => {
    const next = [...filters]
    next.splice(i, 1)
    setFilters(next)
  }

  const isLoading = listQuery.isLoading || metaQuery.isLoading
  const error = listQuery.error || metaQuery.error

  if (isLoading) return <Loading />
  if (error) return <ErrorFallback error={error} message="Could not load saved reports." onRetry={() => listQuery.refetch()} />

  const chartData = (result?.rows ?? []).map((r) => ({ name: String(r.group), count: Number(r.count) }))

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-5xl space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">Saved Reports</h1>

        <DataTable<SavedReport>
          caption="Saved reports"
          columns={[
            { key: 'name', header: 'Name' },
            { key: 'entity', header: 'Entity' },
            { key: 'groupBy', header: 'Group By' },
            {
              key: 'actions',
              header: 'Actions',
              render: (row) => (
                <div className="flex gap-2">
                  <button onClick={() => runMutation.mutate(row)} className="rounded-md border border-border p-1.5 transition hover:bg-muted" title="Run">
                    <Play className="h-4 w-4" />
                  </button>
                  <button onClick={() => loadEdit(row)} className="rounded-md border border-border p-1.5 transition hover:bg-muted" title="Edit">
                    <Plus className="h-4 w-4" />
                  </button>
                  <button onClick={() => deleteMutation.mutate(row.id)} className="rounded-md border border-border p-1.5 transition hover:bg-muted" title="Delete">
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              ),
            },
          ]}
          data={listQuery.data ?? []}
          getRowKey={(row) => row.id}
          emptyText="No saved reports."
        />

        <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold">{editing ? 'Edit Report' : 'Create Report'}</h2>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <div className="space-y-2">
              <label className="text-sm font-medium">Name</label>
              <input value={name} onChange={(e) => setName(e.target.value)} className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">Entity</label>
              <select
                value={entity}
                onChange={(e) => { setEntity(e.target.value); setGroupBy('') }}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              >
                <option value="">Select…</option>
                {metaQuery.data?.entities.map((e) => <option key={e} value={e}>{e}</option>)}
              </select>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">Group By</label>
              <select
                value={groupBy}
                onChange={(e) => setGroupBy(e.target.value)}
                disabled={!entity}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring disabled:opacity-50"
              >
                <option value="">(no grouping)</option>
                {availableFields.map((f) => <option key={f} value={f}>{f}</option>)}
              </select>
            </div>

            <div className="space-y-2 md:col-span-3">
              <label className="text-sm font-medium">Date Range</label>
              <div className="flex gap-2">
                <input type="datetime-local" value={from} onChange={(e) => setFrom(e.target.value)} className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
                <input type="datetime-local" value={to} onChange={(e) => setTo(e.target.value)} className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
              </div>
            </div>
          </div>

          <div className="mt-4 space-y-2">
            <label className="text-sm font-medium">Filters</label>
            {filters.map((f, i) => (
              <div key={i} className="flex flex-wrap gap-2">
                <select value={f.field} onChange={(e) => updateFilter(i, { field: e.target.value })} disabled={!entity} className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring disabled:opacity-50">
                  <option value="">Field</option>
                  {availableFields.map((field) => <option key={field} value={field}>{field}</option>)}
                </select>
                <select value={f.op} onChange={(e) => updateFilter(i, { op: e.target.value })} className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring">
                  <option value="">Op</option>
                  {metaQuery.data?.operators.map((op) => <option key={op} value={op}>{op}</option>)}
                </select>
                <input value={f.value} onChange={(e) => updateFilter(i, { value: e.target.value })} placeholder="Value" className="flex-1 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
                <button onClick={() => removeFilter(i)} className="rounded-md border border-border px-3 py-2 text-sm transition hover:bg-muted">Remove</button>
              </div>
            ))}
            <button onClick={addFilter} className="rounded-md border border-border px-3 py-2 text-sm transition hover:bg-muted">Add Filter</button>
          </div>

          <div className="mt-6 flex gap-2">
            <button
              onClick={() => saveMutation.mutate()}
              disabled={!name || !entity || saveMutation.isPending}
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
            >
              <Save className="h-4 w-4" />
              {editing ? 'Update' : 'Create'}
            </button>
            <button onClick={resetForm} className="rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-muted">Reset</button>
          </div>
          {saveMutation.error && <p className="mt-2 text-sm text-destructive">{saveMutation.error.message}</p>}
        </div>

        {runMutation.isPending && <Loading />}
        {result && (
          <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
            <h2 className="mb-4 text-lg font-semibold">Run Results</h2>
            {result.groupBy ? (
              <div className="h-96">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="name" />
                    <YAxis />
                    <Tooltip />
                    <Bar dataKey="count" fill="#3b82f6">
                      {chartData.map((_, i) => <Cell key={i} fill={['#3b82f6', '#f59e0b', '#10b981', '#ef4444'][i % 4]} />)}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            ) : (
              <DataTable<any>
                caption="Saved report results"
                columns={Object.keys(result.rows[0] ?? {}).map((k) => ({ key: k, header: k }))}
                data={result.rows}
                getRowKey={(row) => JSON.stringify(row)}
                emptyText="No rows."
              />
            )}
          </div>
        )}
      </div>
    </div>
  )
}
