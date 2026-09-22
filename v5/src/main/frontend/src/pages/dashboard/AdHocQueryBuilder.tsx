import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
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
  group: string | null
  groupKey?: string | null
  count: number
}

type DetailRow = Record<string, unknown> & { detailUrl?: string }

interface QueryResponse {
  rows: (QueryRow | DetailRow)[]
  groupBy: string | null
  total?: number
  page?: number
  pageSize?: number
}

interface SavedReport {
  id: string
  name: string
  entity: string
  filters: string
  groupBy: string
  dateRange: string
}

/** Date fields available per entity — mirrors the backend whitelist. */
const DATE_FIELDS_BY_ENTITY: Record<string, { value: string; label: string }[]> = {
  incident: [
    { value: 'createdAt', label: 'Created' },
    { value: 'resolvedAt', label: 'Resolved' },
    { value: 'closedAt', label: 'Closed' },
  ],
  service_request: [
    { value: 'createdAt', label: 'Created' },
    { value: 'decidedAt', label: 'Approval decided' },
  ],
  problem: [
    { value: 'createdAt', label: 'Created' },
    { value: 'resolvedAt', label: 'Resolved' },
    { value: 'closedAt', label: 'Closed' },
  ],
  change: [{ value: 'createdAt', label: 'Created' }],
  issue: [{ value: 'createdAt', label: 'Created' }],
}

const OPEN_INCIDENT = 'NEW,ASSIGNED,IN_PROGRESS,ON_HOLD,WAITING_ON_CUSTOMER,REOPENED'
const OPEN_REQUEST = 'SUBMITTED,PENDING_APPROVAL,APPROVED,IN_FULFILLMENT,ON_HOLD,REJECTED_NEEDS_REVIEW'

interface BuilderTemplate {
  name: string
  entity: string
  detailed: boolean
  groupBy?: string
  filters?: QueryFilter[]
  days?: number
  dateField?: string
  /** Only `to` — for "older than" templates. */
  olderThanDays?: number
}

const BUILDER_TEMPLATES: BuilderTemplate[] = [
  { name: 'Open Incidents by Agent', entity: 'incident', detailed: false, groupBy: 'assignee', filters: [{ field: 'status', op: 'in', value: OPEN_INCIDENT }] },
  { name: 'Open Critical Incidents', entity: 'incident', detailed: true, filters: [{ field: 'status', op: 'in', value: OPEN_INCIDENT }, { field: 'priority', op: 'eq', value: 'Critical' }] },
  { name: 'Incidents Resolved This Week', entity: 'incident', detailed: true, days: 7, dateField: 'resolvedAt', filters: [{ field: 'status', op: 'in', value: 'RESOLVED,CLOSED' }] },
  { name: 'Pending Approvals Older Than 3 Days', entity: 'service_request', detailed: true, olderThanDays: 3, filters: [{ field: 'status', op: 'eq', value: 'PENDING_APPROVAL' }] },
  { name: 'Service Requests Awaiting Fulfillment', entity: 'service_request', detailed: true, filters: [{ field: 'status', op: 'in', value: 'APPROVED,IN_FULFILLMENT' }] },
  { name: 'Reopened Incidents Last 30 Days', entity: 'incident', detailed: true, days: 30, filters: [{ field: 'status', op: 'eq', value: 'REOPENED' }] },
  { name: 'Incidents by Location', entity: 'incident', detailed: false, groupBy: 'location' },
  { name: 'Open Requests by Catalog Item', entity: 'service_request', detailed: false, groupBy: 'catalogItem', filters: [{ field: 'status', op: 'in', value: OPEN_REQUEST }] },
]

interface CannedTemplate {
  name: string
  entity: string
  dimension: 'agent' | 'location'
  breachedOnly?: boolean
}

const CANNED_TEMPLATES: CannedTemplate[] = [
  { name: 'SLA Breaches by Location', entity: 'incident', dimension: 'location', breachedOnly: true },
  { name: 'Incident SLA Compliance by Agent', entity: 'incident', dimension: 'agent' },
  { name: 'Incident SLA Compliance by Location', entity: 'incident', dimension: 'location' },
  { name: 'Service Request SLA Compliance by Agent (fulfiller)', entity: 'service_request', dimension: 'agent' },
  { name: 'Service Request SLA Compliance by Location', entity: 'service_request', dimension: 'location' },
]

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
  const [mode, setMode] = useState<'grouped' | 'detailed'>('grouped')
  const [groupBy, setGroupBy] = useState('')
  const [dateField, setDateField] = useState('createdAt')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<QueryFilter[]>([{ field: '', op: '', value: '' }])
  const [drillContext, setDrillContext] = useState<string | null>(null)
  const [saveOpen, setSaveOpen] = useState(false)
  const [saveName, setSaveName] = useState('')
  const [saveError, setSaveError] = useState<string | null>(null)
  const [pendingDelete, setPendingDelete] = useState<SavedReport | null>(null)

  const availableFields = entity ? (metaQuery.data?.fieldsByEntity[entity] ?? []) : []
  const dateFields = DATE_FIELDS_BY_ENTITY[entity] ?? [{ value: 'createdAt', label: 'Created' }]

  const buildBody = (overrides?: {
    detailed?: boolean; filters?: QueryFilter[]; page?: number; groupBy?: string
  }) => {
    const body: Record<string, unknown> = { entity }
    const gb = overrides?.groupBy !== undefined ? overrides.groupBy : groupBy
    if (gb) body.groupBy = gb
    if (from || to) {
      body.dateRange = { from: from ? toIso(from) : null, to: to ? toIso(to) : null, dateField }
    }
    body.filters = (overrides?.filters ?? filters).filter((f) => f.field && f.op)
    if (overrides?.detailed ?? (mode === 'detailed')) {
      body.detailed = true
      body.page = overrides?.page ?? page
      body.pageSize = 50
    }
    return body
  }

  const queryMutation = useMutation<QueryResponse, Error, Record<string, unknown>>({
    mutationFn: async (body) => {
      const res = await fetchWithToken(instance, account!, '/api/v1/reports/query', { method: 'POST', body: JSON.stringify(body) })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
  })

  const run = (overrides?: Parameters<typeof buildBody>[0]) => {
    runSaved.reset()
    queryMutation.mutate(buildBody(overrides))
  }

  /** Grouped → detailed: run the same query plus the clicked group's filter. */
  const drillInto = (row: QueryRow) => {
    if (!groupBy) return
    const drillFilter: QueryFilter = { field: groupBy, op: 'eq', value: row.groupKey ?? '' }
    const next = [...filters.filter((f) => f.field && f.op), drillFilter]
    setFilters(next)
    setMode('detailed')
    setPage(0)
    setDrillContext(`${groupBy} = ${row.group ?? 'Unassigned'}`)
    run({ detailed: true, filters: next, page: 0 })
  }

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
          groupBy: mode === 'grouped' ? groupBy : '',
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

  const runSaved = useMutation<QueryResponse, Error, SavedReport>({
    mutationFn: async (report) => {
      const res = await fetchWithToken(instance, account!, '/api/v1/reports/query', {
        method: 'POST',
        body: JSON.stringify({
          entity: report.entity,
          groupBy: report.groupBy || undefined,
          filters: parseJson<QueryFilter[]>(report.filters, []),
          dateRange: parseJson<{ from: string; to: string; dateField?: string } | null>(report.dateRange, null) ?? undefined,
        }),
      })
      if (!res.ok) throw new Error((await res.text()) || `HTTP ${res.status}`)
      return res.json()
    },
  })

  const loadSaved = (report: SavedReport) => {
    setEntity(report.entity)
    setMode('grouped')
    setGroupBy(report.groupBy ?? '')
    const dr = parseJson<{ from: string; to: string; dateField?: string } | null>(report.dateRange, null)
    setFrom(dr?.from ? toEasternInputValue(dr.from) : '')
    setTo(dr?.to ? toEasternInputValue(dr.to) : '')
    setDateField(dr?.dateField ?? 'createdAt')
    const f = parseJson<QueryFilter[]>(report.filters, [])
    setFilters(f.length ? f : [{ field: '', op: '', value: '' }])
    setDrillContext(null)
    queryMutation.reset()
    runSaved.reset()
  }

  const applyTemplate = (t: BuilderTemplate) => {
    setEntity(t.entity)
    setMode(t.detailed ? 'detailed' : 'grouped')
    setGroupBy(t.groupBy ?? '')
    const now = new Date()
    const newFrom = t.days ? new Date(now.getTime() - t.days * 86400000).toISOString().slice(0, 16) : ''
    const newTo = t.olderThanDays ? new Date(now.getTime() - t.olderThanDays * 86400000).toISOString().slice(0, 16) : ''
    const newDateField = t.dateField ?? 'createdAt'
    setFrom(newFrom)
    setTo(newTo)
    setDateField(newDateField)
    setFilters(t.filters ?? [{ field: '', op: '', value: '' }])
    setPage(0)
    setDrillContext(null)
    const body: Record<string, unknown> = { entity: t.entity }
    if (t.groupBy && !t.detailed) body.groupBy = t.groupBy
    if (newFrom || newTo) {
      body.dateRange = { from: newFrom ? toIso(newFrom) : null, to: newTo ? toIso(newTo) : null, dateField: newDateField }
    }
    body.filters = t.filters ?? []
    if (t.detailed) {
      body.detailed = true
      body.page = 0
      body.pageSize = 50
    }
    runSaved.reset()
    queryMutation.mutate(body)
  }

  const [canned, setCanned] = useState<CannedTemplate | null>(null)
  const cannedQuery = useQuery<{ name: string; total: number; breached: number; compliancePercent: number }[]>({
    queryKey: ['sla-by-dimension', canned?.entity, canned?.dimension],
    queryFn: async () => {
      const res = await fetchWithToken(
        instance, account!,
        `/api/v1/reports/sla-compliance/by-dimension?entity=${canned!.entity}&dimension=${canned!.dimension}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && !!canned,
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

  const results = queryMutation.data ?? runSaved.data
  const resultsPending = queryMutation.isPending || runSaved.isPending
  const isDetailed = (results?.rows?.[0] as DetailRow | undefined)?.detailUrl !== undefined
    || (results?.total !== undefined && results?.page !== undefined)

  const chartData = useMemo(() => {
    return ((results?.rows ?? []) as QueryRow[]).map((r) => ({
      name: String(r.group ?? 'Unassigned'),
      count: Number(r.count),
      groupKey: r.groupKey,
      group: r.group,
    }))
  }, [results])

  const detailColumns = useMemo(() => {
    const rows = (results?.rows ?? []) as DetailRow[]
    const keys = Object.keys(rows[0] ?? {}).filter((k) => k !== 'id' && k !== 'detailUrl')
    return keys.map((k) => ({
      key: k,
      header: k === 'ref' ? 'Ref' : k.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase()),
      render: k === 'ref'
        ? (r: DetailRow) => r.detailUrl
            ? <Link to={r.detailUrl} className="font-medium text-primary hover:underline">{String(r[k] ?? '')}</Link>
            : String(r[k] ?? '')
        : (r: DetailRow) => {
            const v = r[k]
            if (typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(v)) return v.slice(0, 16).replace('T', ' ')
            return String(v ?? '—')
          },
    }))
  }, [results])

  const totalPages = results?.total !== undefined && results?.pageSize
    ? Math.max(1, Math.ceil(results.total / results.pageSize))
    : 1

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
          Build a grouped or row-level report, save it for reuse, or start from a template. Click any grouped result to drill into the actual records.
        </p>

        <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-3 text-lg font-semibold">Templates</h2>
          <div className="flex flex-wrap gap-2">
            {BUILDER_TEMPLATES.map((t) => (
              <button
                key={t.name}
                onClick={() => applyTemplate(t)}
                className="rounded-md border border-border px-3 py-1.5 text-xs font-medium transition hover:bg-muted"
              >
                {t.name}
              </button>
            ))}
          </div>
          <h3 className="mb-2 mt-4 text-sm font-semibold text-muted-foreground">SLA compliance</h3>
          <div className="flex flex-wrap gap-2">
            {CANNED_TEMPLATES.map((t) => (
              <button
                key={t.name}
                onClick={() => setCanned(t)}
                className={`rounded-md border px-3 py-1.5 text-xs font-medium transition ${
                  canned?.name === t.name ? 'border-primary bg-primary/10' : 'border-border hover:bg-muted'
                }`}
              >
                {t.name}
              </button>
            ))}
          </div>
          {canned && (
            <div className="mt-4">
              {cannedQuery.isLoading ? (
                <Loading />
              ) : cannedQuery.error ? (
                <p className="text-sm text-destructive">Could not load report.</p>
              ) : (() => {
                const rows = (cannedQuery.data ?? []).filter((r) => !canned.breachedOnly || r.breached > 0)
                return rows.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No data.</p>
                ) : (
                  <DataTable
                    caption={canned.name}
                    columns={[
                      { key: 'name', header: canned.dimension === 'agent' ? 'Agent' : 'Location',
                        render: (r) => {
                          const base = canned.entity === 'service_request' ? '/dashboard/service-requests' : '/dashboard/incidents'
                          const param = canned.dimension === 'agent'
                            ? (canned.entity === 'incident' ? `?assignee=${encodeURIComponent(r.name)}` : null)
                            : `?location=${encodeURIComponent(r.name)}`
                          return param ? <Link to={base + param} className="font-medium text-primary hover:underline">{r.name}</Link> : r.name
                        } },
                      { key: 'total', header: 'SLA Tickets' },
                      { key: 'breached', header: 'Breached' },
                      { key: 'compliancePercent', header: 'Compliance %', render: (r) => `${r.compliancePercent}%` },
                    ]}
                    data={rows}
                    getRowKey={(r) => r.name}
                    emptyText="No data."
                  />
                )
              })()}
            </div>
          )}
        </div>

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
          <div className="mb-4 flex gap-1 rounded-lg border border-border p-1 w-fit">
            <button
              onClick={() => setMode('grouped')}
              className={`rounded-md px-4 py-1.5 text-sm font-medium transition ${mode === 'grouped' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'}`}
            >
              Grouped / Count
            </button>
            <button
              onClick={() => setMode('detailed')}
              className={`rounded-md px-4 py-1.5 text-sm font-medium transition ${mode === 'detailed' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'}`}
            >
              Detailed (rows)
            </button>
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">Entity</label>
              <select
                value={entity}
                onChange={(e) => { setEntity(e.target.value); setGroupBy(''); setDateField('createdAt') }}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              >
                <option value="">Select…</option>
                {metaQuery.data?.entities.map((e) => <option key={e} value={e}>{e}</option>)}
              </select>
            </div>

            {mode === 'grouped' ? (
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
            ) : (
              <div className="space-y-2">
                <label className="text-sm font-medium">Mode</label>
                <p className="rounded-md border border-dashed border-border px-3 py-2 text-sm text-muted-foreground">
                  Returns matching records with links, 50 per page.
                </p>
              </div>
            )}

            <div className="space-y-2 md:col-span-2">
              <div className="flex items-center justify-between">
                <label className="text-sm font-medium">Date Range</label>
                <select
                  value={dateField}
                  onChange={(e) => setDateField(e.target.value)}
                  className="rounded-md border border-input bg-background px-2 py-1 text-xs outline-none focus:ring-2 focus:ring-ring"
                  title="Which date the range applies to"
                >
                  {dateFields.map((d) => <option key={d.value} value={d.value}>{d.label} date</option>)}
                </select>
              </div>
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
                  placeholder="Value (name or id; blank = unassigned)"
                  className="flex-1 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                />
                <button onClick={() => removeFilter(i)} className="rounded-md border border-border px-3 py-2 text-sm transition hover:bg-muted">Remove</button>
              </div>
            ))}
            <button onClick={addFilter} className="rounded-md border border-border px-3 py-2 text-sm transition hover:bg-muted">Add Filter</button>
          </div>

          <div className="mt-6 flex flex-wrap items-center gap-2">
            <button
              onClick={() => { setDrillContext(null); run() }}
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
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-lg font-semibold">Results</h2>
              {drillContext && (
                <span className="rounded-md bg-muted px-2 py-1 text-xs text-muted-foreground">
                  Drilled into {drillContext}
                </span>
              )}
            </div>
            {isDetailed ? (
              <>
                <DataTable<DetailRow>
                  caption="Query results"
                  columns={detailColumns}
                  data={results.rows as DetailRow[]}
                  getRowKey={(row) => String(row.id ?? JSON.stringify(row))}
                  emptyText="No matching records."
                />
                <div className="mt-4 flex items-center justify-between text-sm text-muted-foreground">
                  <span>
                    {results.total ?? results.rows.length} record{(results.total ?? results.rows.length) === 1 ? '' : 's'}
                    {results.total !== undefined ? ` · page ${(results.page ?? 0) + 1} of ${totalPages}` : ''}
                  </span>
                  {results.total !== undefined && (
                    <div className="flex gap-2">
                      <button
                        onClick={() => { setPage((p) => p - 1); run({ detailed: true, page: page - 1 }) }}
                        disabled={page <= 0}
                        className="rounded-md border border-border px-3 py-1 text-xs transition hover:bg-muted disabled:opacity-50"
                      >
                        Previous
                      </button>
                      <button
                        onClick={() => { setPage((p) => p + 1); run({ detailed: true, page: page + 1 }) }}
                        disabled={page + 1 >= totalPages}
                        className="rounded-md border border-border px-3 py-1 text-xs transition hover:bg-muted disabled:opacity-50"
                      >
                        Next
                      </button>
                    </div>
                  )}
                </div>
              </>
            ) : results.groupBy ? (
              <>
                <p className="mb-2 text-xs text-muted-foreground">Click a row or bar to see the actual records.</p>
                <div className="h-96">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartData}>
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="name" />
                      <YAxis />
                      <Tooltip />
                      <Bar dataKey="count" fill="#3b82f6" onClick={(d) => drillInto(d.payload as unknown as QueryRow)} className="cursor-pointer">
                        {chartData.map((_, i) => <Cell key={i} fill={['#3b82f6', '#f59e0b', '#10b981', '#ef4444'][i % 4]} />)}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
                <DataTable<QueryRow>
                  caption="Grouped results — click a row to drill in"
                  columns={[
                    { key: 'group', header: String(results.groupBy), render: (r) => (
                      <button onClick={() => drillInto(r)} className="font-medium text-primary hover:underline">
                        {r.group ?? 'Unassigned'}
                      </button>
                    ) },
                    { key: 'count', header: 'Count' },
                  ]}
                  data={results.rows as QueryRow[]}
                  getRowKey={(row) => String(row.group ?? 'null')}
                  emptyText="No rows."
                />
              </>
            ) : (
              <DataTable<QueryRow>
                caption="Query results"
                columns={Object.keys(results.rows[0] ?? {}).map((k) => ({ key: k, header: k }))}
                data={results.rows as QueryRow[]}
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
