import { useMemo, useState } from 'react'
import { DateTimeInput } from '../../components/ui/DateTimeInput'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Play, Save, Trash2 } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { downloadCsv } from '../../lib/csv'
import { DataTable } from '../../components/ui/DataTable'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { Loading } from '../../components/ui/Loading'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { toEasternInputValue } from '../../lib/date'

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

interface SavedReport {
  id: string
  name: string
  entity: string
  filters: string
  groupBy: string
  dateRange: string
}

function parseJson<T>(value: string | null | undefined, fallback: T): T {
  try {
    return value ? (JSON.parse(value) as T) : fallback
  } catch {
    return fallback
  }
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

  const queryClient = useQueryClient()
  const [entity, setEntity] = useState('')
  const [groupBy, setGroupBy] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [filters, setFilters] = useState<QueryFilter[]>([{ field: '', op: '', value: '' }])
  const [saveOpen, setSaveOpen] = useState(false)
  const [saveName, setSaveName] = useState('')
  const [saveError, setSaveError] = useState<string | null>(null)
  const [pendingDelete, setPendingDelete] = useState<SavedReport | null>(null)

  const availableFields = entity ? (metaQuery.data?.fieldsByEntity[entity] ?? []) : []

  const buildBody = () => {
    const body: Record<string, unknown> = { entity }
    if (groupBy) body.groupBy = groupBy
    if (from || to) {
      body.dateRange = { from: from ? toIso(from) : null, to: to ? toIso(to) : null }
    }
    body.filters = filters.filter((f) => f.field && f.op)
    return body
  }

  const queryMutation = useMutation<{ rows: QueryRow[]; groupBy: string | null }, Error>({
    mutationFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/reports/query', { method: 'POST', body: JSON.stringify(buildBody()) })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
  })

  // Server-side XLSX export of the same query — for row-level exports larger
  // than what the on-screen result set comfortably shows.
  const exportXlsx = async () => {
    const res = await fetchWithToken(instance, account!, '/api/v1/reports/query?format=xlsx', {
      method: 'POST',
      body: JSON.stringify(buildBody()),
    })
    if (!res.ok) {
      const text = await res.text()
      throw new Error(text || `HTTP ${res.status}`)
    }
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${entity || 'report'}-export.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  }

  // Saved queries live here alongside the builder — one place to build, save,
  // and re-run custom reports.
  const savedQuery = useQuery<SavedReport[]>({
    queryKey: ['saved-reports'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/reports/saved')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const saveMutation = useMutation<SavedReport, Error, void>({
    mutationFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/reports/saved', {
        method: 'POST',
        body: JSON.stringify({
          name: saveName.trim(),
          entity,
          groupBy,
          filters: filters.filter((f) => f.field && f.op),
          dateRange: from || to ? { from: from ? toIso(from) : null, to: to ? toIso(to) : null } : null,
        }),
      })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['saved-reports'] })
      setSaveOpen(false)
      setSaveName('')
      setSaveError(null)
    },
    onError: (e) => setSaveError(e.message),
  })

  const deleteMutation = useMutation<void, Error, string>({
    mutationFn: async (id) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/reports/saved/${id}`, { method: 'DELETE' })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['saved-reports'] }),
  })

  const runSaved = useMutation<{ rows: QueryRow[]; groupBy: string | null }, Error, SavedReport>({
    mutationFn: async (report) => {
      const res = await fetchWithToken(instance, account!, '/api/v1/reports/query', {
        method: 'POST',
        body: JSON.stringify({
          entity: report.entity,
          groupBy: report.groupBy || undefined,
          filters: parseJson<QueryFilter[]>(report.filters, []),
          dateRange: parseJson<{ from: string; to: string } | null>(report.dateRange, null) ?? undefined,
        }),
      })
      if (!res.ok) throw new Error((await res.text()) || `HTTP ${res.status}`)
      return res.json()
    },
  })

  const loadSaved = (report: SavedReport) => {
    setEntity(report.entity)
    setGroupBy(report.groupBy ?? '')
    const dr = parseJson<{ from: string; to: string } | null>(report.dateRange, null)
    setFrom(dr?.from ? toEasternInputValue(dr.from) : '')
    setTo(dr?.to ? toEasternInputValue(dr.to) : '')
    const f = parseJson<QueryFilter[]>(report.filters, [])
    setFilters(f.length ? f : [{ field: '', op: '', value: '' }])
    queryMutation.reset()
    runSaved.reset()
  }

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

  const results = queryMutation.data ?? runSaved.data
  const resultsPending = queryMutation.isPending || runSaved.isPending

  const chartData = useMemo(() => {
    return (results?.rows ?? []).map((r) => ({
      name: String(r.group),
      count: Number(r.count),
    }))
  }, [results])

  if (metaQuery.isLoading) return <Loading />
  if (metaQuery.error) return <ErrorFallback error={metaQuery.error} message="Could not load query metadata." onRetry={() => metaQuery.refetch()} />

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete saved query?"
        description={`The saved query "${pendingDelete?.name}" will be permanently deleted.`}
        confirmLabel="Delete"
        destructive
        onConfirm={() => {
          if (pendingDelete) deleteMutation.mutate(pendingDelete.id)
          setPendingDelete(null)
        }}
        onCancel={() => setPendingDelete(null)}
      />
      <div className="mx-auto max-w-5xl space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">Query Builder</h1>
        <p className="text-sm text-muted-foreground">
          Build your own grouped report, save it for reuse, or run a saved query.
        </p>

        <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-3 text-lg font-semibold">Saved Queries</h2>
          {(savedQuery.data ?? []).length === 0 ? (
            <p className="text-sm text-muted-foreground">No saved queries yet — build a query below and save it.</p>
          ) : (
            <ul className="divide-y divide-border">
              {(savedQuery.data ?? []).map((r) => (
                <li key={r.id} className="flex items-center justify-between gap-3 py-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{r.name}</p>
                    <p className="text-xs text-muted-foreground">
                      {r.entity.replace(/_/g, ' ')}{r.groupBy ? ` · grouped by ${r.groupBy}` : ''}
                    </p>
                  </div>
                  <div className="flex shrink-0 gap-2">
                    <button
                      onClick={() => runSaved.mutate(r)}
                      className="inline-flex items-center gap-1 rounded-md border border-border px-2 py-1 text-xs transition hover:bg-muted"
                      title="Run"
                    >
                      <Play className="h-3.5 w-3.5" /> Run
                    </button>
                    <button
                      onClick={() => loadSaved(r)}
                      className="rounded-md border border-border px-2 py-1 text-xs transition hover:bg-muted"
                      title="Load into the builder below"
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => setPendingDelete(r)}
                      className="rounded-md border border-border p-1.5 transition hover:bg-muted"
                      title="Delete"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

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
                <DateTimeInput
                  value={from}
                  onChange={(e) => setFrom(e.target.value)}
                  className="min-w-0"
                />
                <DateTimeInput
                  value={to}
                  onChange={(e) => setTo(e.target.value)}
                  className="min-w-0"
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

          <div className="mt-6 flex flex-wrap items-center gap-2">
            <button
              onClick={() => { runSaved.reset(); queryMutation.mutate() }}
              disabled={!entity || queryMutation.isPending}
              className="inline-flex rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
            >
              Run Query
            </button>
            <button
              onClick={() => { setSaveOpen(true); setSaveError(null) }}
              disabled={!entity}
              className="inline-flex items-center gap-1.5 rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-muted disabled:opacity-50"
            >
              <Save className="h-4 w-4" />
              Save Query
            </button>
            {saveOpen && (
              <span className="inline-flex items-center gap-2">
                <input
                  value={saveName}
                  onChange={(e) => setSaveName(e.target.value)}
                  placeholder="Query name"
                  className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                />
                <button
                  onClick={() => {
                    if (!saveName.trim()) { setSaveError('A name is required'); return }
                    saveMutation.mutate()
                  }}
                  disabled={saveMutation.isPending}
                  className="rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
                >
                  Save
                </button>
                <button onClick={() => setSaveOpen(false)} className="text-sm text-muted-foreground hover:text-foreground">Cancel</button>
              </span>
            )}
            {results && (
              <>
                <button
                  onClick={() => downloadCsv(`${entity || 'query'}-export.csv`, results.rows)}
                  className="inline-flex items-center gap-1.5 rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-muted"
                >
                  Export CSV
                </button>
                <button
                  onClick={() => { exportXlsx().catch((e) => console.error(e)) }}
                  className="inline-flex items-center gap-1.5 rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-muted"
                >
                  Export Excel
                </button>
              </>
            )}
          </div>
          {(queryMutation.error || runSaved.error || saveError) && (
            <p className="mt-2 text-sm text-destructive">
              {queryMutation.error?.message ?? runSaved.error?.message ?? saveError}
            </p>
          )}
        </div>

        {resultsPending && <Loading />}

        {results && (
          <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
            <h2 className="mb-4 text-lg font-semibold">Results</h2>
            {results.groupBy ? (
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
                columns={Object.keys(results.rows[0] ?? {}).map((k) => ({ key: k, header: k }))}
                data={results.rows}
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
