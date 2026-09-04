import { useMemo, useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { fetchWithToken } from '../../api/client'
import { DataTable } from '../../components/ui/DataTable'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { Loading } from '../../components/ui/Loading'

interface QueryFilter {
  field: string
  op: string
  value: string
}

interface ReportMetadata {
  entities: string[]
  fieldsByEntity: Record<string, string[]>
  operators: string[]
  dateFieldByEntity: Record<string, string>
}

interface QueryRow {
  group: string
  count: number
}

function toIso(input: string) {
  if (!input) return ''
  return input.endsWith('Z') ? input : input + ':00Z'
}

export function AdHocQueryBuilder() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]

  const metaQuery = useQuery<ReportMetadata>({
    queryKey: ['reports-metadata'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/reports/metadata')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const [entity, setEntity] = useState('')
  const [groupBy, setGroupBy] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [filters, setFilters] = useState<QueryFilter[]>([{ field: '', op: '', value: '' }])

  const availableFields = entity ? (metaQuery.data?.fieldsByEntity[entity] ?? []) : []

  const queryMutation = useMutation<{ rows: QueryRow[]; groupBy: string | null }, Error>({
    mutationFn: async () => {
      const body: Record<string, unknown> = { entity }
      if (groupBy) body.groupBy = groupBy
      if (from || to) {
        body.dateRange = { from: from ? toIso(from) : null, to: to ? toIso(to) : null }
      }
      body.filters = filters.filter((f) => f.field && f.op)
      const res = await fetchWithToken(instance, account!, '/api/v1/reports/query', { method: 'POST', body: JSON.stringify(body) })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
  })

  const addFilter = () => setFilters([...filters, { field: '', op: '', value: '' }])
  const updateFilter = (i: number, patch: Partial<QueryFilter>) => {
    const next = [...filters]
    next[i] = { ...next[i], ...patch }
    setFilters(next)
  }
  const removeFilter = (i: number) => {
    const next = [...filters]
    next.splice(i, 1)
    setFilters(next)
  }

  const chartData = useMemo(() => {
    return (queryMutation.data?.rows ?? []).map((r) => ({
      name: String(r.group),
      count: Number(r.count),
    }))
  }, [queryMutation.data])

  if (metaQuery.isLoading) return <Loading />
  if (metaQuery.error) return <ErrorFallback error={metaQuery.error} message="Could not load query metadata." onRetry={() => metaQuery.refetch()} />

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-5xl space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">Ad-Hoc Query Builder</h1>

        <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
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

            <div className="space-y-2 md:col-span-2">
              <label className="text-sm font-medium">Date Range</label>
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                <input
                  type="datetime-local"
                  value={from}
                  onChange={(e) => setFrom(e.target.value)}
                  className="min-w-0 w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                />
                <input
                  type="datetime-local"
                  value={to}
                  onChange={(e) => setTo(e.target.value)}
                  className="min-w-0 w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                />
              </div>
            </div>
          </div>

          <div className="mt-4 space-y-2">
            <label className="text-sm font-medium">Filters</label>
            {filters.map((f, i) => (
              <div key={i} className="flex flex-wrap gap-2">
                <select
                  value={f.field}
                  onChange={(e) => updateFilter(i, { field: e.target.value })}
                  disabled={!entity}
                  className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring disabled:opacity-50"
                >
                  <option value="">Field</option>
                  {availableFields.map((field) => <option key={field} value={field}>{field}</option>)}
                </select>
                <select
                  value={f.op}
                  onChange={(e) => updateFilter(i, { op: e.target.value })}
                  className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                >
                  <option value="">Op</option>
                  {metaQuery.data?.operators.map((op) => <option key={op} value={op}>{op}</option>)}
                </select>
                <input
                  value={f.value}
                  onChange={(e) => updateFilter(i, { value: e.target.value })}
                  placeholder="Value"
                  className="flex-1 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                />
                <button onClick={() => removeFilter(i)} className="rounded-md border border-border px-3 py-2 text-sm transition hover:bg-muted">Remove</button>
              </div>
            ))}
            <button onClick={addFilter} className="rounded-md border border-border px-3 py-2 text-sm transition hover:bg-muted">Add Filter</button>
          </div>

          <button
            onClick={() => queryMutation.mutate()}
            disabled={!entity || queryMutation.isPending}
            className="mt-6 inline-flex rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
          >
            Run Query
          </button>
          {queryMutation.error && <p className="mt-2 text-sm text-destructive">{queryMutation.error.message}</p>}
        </div>

        {queryMutation.isPending && <Loading />}

        {queryMutation.data && (
          <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
            <h2 className="mb-4 text-lg font-semibold">Results</h2>
            {queryMutation.data.groupBy ? (
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
                caption="Query results"
                columns={Object.keys(queryMutation.data.rows[0] ?? {}).map((k) => ({ key: k, header: k }))}
                data={queryMutation.data.rows}
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
