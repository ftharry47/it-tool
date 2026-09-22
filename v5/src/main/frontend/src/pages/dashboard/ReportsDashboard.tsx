import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Download, FileQuestion } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { DataTable } from '../../components/ui/DataTable'
import { DateInput } from '../../components/ui/DateInput'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { downloadCsv } from '../../lib/csv'
import { formatDate, formatDateTime } from '../../lib/date'

const COLORS = ['#3b82f6', '#f59e0b', '#10b981', '#ef4444', '#8b5cf6', '#ec4899']

interface TicketsSummary {
  total: number
  open: number
  inProgress: number
  resolvedToday: number
}

interface SlaCompliance {
  total: number
  breached: number
  compliancePercent: number
}

interface AgentWorkload {
  agentName: string
  openCount: number
  incidents: number
  serviceRequests: number
  problems: number
  changes: number
  onTrack: number
  atRisk: number
  breached: number
  noSla: number
}

interface SprintVelocity {
  sprintName: string
  sprintStatus: 'ACTIVE' | 'COMPLETED'
  projectName: string | null
  endDate: string | null
  committed: number
  completed: number
  committedPoints: number
  completedPoints: number
}

interface TrendPoint {
  date: string
  count?: number
  compliancePercent?: number
}

interface MonthlyPoint {
  month: string
  created: number
  closed: number
}

interface LegacySplit {
  legacy: number
  current: number
  byCategory: { category: string; legacy: number; current: number }[]
}

interface AdHocRow {
  group: string | null
  count: number
}

interface AdHocQueryResponse {
  orgId: string
  entity: string
  groupBy: string | null
  rows: AdHocRow[]
}

function TicketsSummaryView({ data, mine, userId }: { data: TicketsSummary; mine: boolean; userId: string | undefined }) {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const [days, setDays] = useState(30)

  const trendQuery = useQuery<TrendPoint[]>({
    queryKey: ['reports', 'tickets-trend', days, mine],
    queryFn: async () => {
      const qs = new URLSearchParams({ days: String(days), mine: String(mine) })
      const res = await fetchWithToken(instance, account!, `/api/v1/reports/tickets-trend?${qs.toString()}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const reportBody = (groupBy: string) => ({
    entity: 'incident',
    groupBy,
    filters: mine && userId ? [{ field: 'assignee', op: 'eq', value: userId }] : undefined,
  })

  const categoryQuery = useQuery<AdHocQueryResponse>({
    queryKey: ['reports', 'category-breakdown', mine],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/reports/query', {
        method: 'POST',
        body: JSON.stringify(reportBody('category')),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const priorityQuery = useQuery<AdHocQueryResponse>({
    queryKey: ['reports', 'priority-breakdown', mine],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/reports/query', {
        method: 'POST',
        body: JSON.stringify(reportBody('priority')),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const monthlyQuery = useQuery<MonthlyPoint[]>({
    queryKey: ['reports', 'tickets-monthly', mine],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/reports/tickets-monthly?months=12&mine=${mine}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const legacyQuery = useQuery<LegacySplit>({
    queryKey: ['reports', 'legacy-split', mine],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/reports/legacy-split?mine=${mine}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const closed = Math.max(0, data.total - data.open - data.inProgress - data.resolvedToday)
  const statusChartData = [
    { name: 'Open', value: data.open },
    { name: 'In Progress', value: data.inProgress },
    { name: 'Resolved Today', value: data.resolvedToday },
    { name: 'Closed', value: closed },
  ].filter((d) => d.value > 0)

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <MetricCard label="Total" value={data.total} />
        <MetricCard label="Open" value={data.open} />
        <MetricCard label="In Progress" value={data.inProgress} />
        <MetricCard label="Resolved Today" value={data.resolvedToday} />
      </div>

      <div className="rounded-xl border border-border bg-card p-4 shadow-sm space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-medium text-muted-foreground">Ticket volume trend</h3>
          <select
            value={days}
            onChange={(e) => setDays(Number(e.target.value))}
            className="rounded-md border border-border bg-background px-2 py-1 text-sm"
          >
            <option value={7}>Last 7 days</option>
            <option value={30}>Last 30 days</option>
          </select>
        </div>
        {trendQuery.isLoading ? (
          <Loading />
        ) : trendQuery.error ? (
          <ErrorFallback error={trendQuery.error} message="Could not load ticket trend." onRetry={() => trendQuery.refetch()} />
        ) : (
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={trendQuery.data}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="date" tick={{ fontSize: 12 }} />
                <YAxis />
                <Tooltip />
                <Line type="monotone" dataKey="count" stroke={COLORS[0]} strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
          <h3 className="mb-2 text-sm font-medium text-muted-foreground">Tickets by status</h3>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={statusChartData} dataKey="value" nameKey="name" outerRadius={70} label>
                  {statusChartData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </div>
        <BreakdownBarChart title="Tickets by category" query={categoryQuery} />
        <BreakdownBarChart title="Tickets by priority" query={priorityQuery} />
      </div>

      <div className="rounded-xl border border-border bg-card p-4 shadow-sm space-y-3">
        <h3 className="text-sm font-medium text-muted-foreground">Tickets by month (includes imported history)</h3>
        {monthlyQuery.isLoading ? (
          <Loading />
        ) : monthlyQuery.error ? (
          <ErrorFallback error={monthlyQuery.error} message="Could not load monthly volume." onRetry={() => monthlyQuery.refetch()} />
        ) : (
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={monthlyQuery.data} margin={{ top: 10, right: 20, left: 0, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="month" tick={{ fontSize: 12 }} />
                <YAxis allowDecimals={false} />
                <Tooltip />
                <Legend />
                <Bar dataKey="created" name="Created" fill={COLORS[0]} maxBarSize={40} radius={[4, 4, 0, 0]} />
                <Bar dataKey="closed" name="Closed" fill={COLORS[2]} maxBarSize={40} radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
          <h3 className="mb-2 text-sm font-medium text-muted-foreground">Legacy vs current tickets</h3>
          {legacyQuery.isLoading ? (
            <Loading />
          ) : legacyQuery.error ? (
            <ErrorFallback error={legacyQuery.error} message="Could not load legacy split." onRetry={() => legacyQuery.refetch()} />
          ) : (legacyQuery.data?.legacy ?? 0) + (legacyQuery.data?.current ?? 0) === 0 ? (
            <p className="text-sm text-muted-foreground">No data.</p>
          ) : (
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={[
                      { name: 'Imported (legacy)', value: legacyQuery.data!.legacy },
                      { name: 'Current system', value: legacyQuery.data!.current },
                    ].filter((d) => d.value > 0)}
                    dataKey="value"
                    nameKey="name"
                    innerRadius={45}
                    outerRadius={70}
                    label
                  >
                    <Cell fill={COLORS[4]} />
                    <Cell fill={COLORS[0]} />
                  </Pie>
                  <Tooltip />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>
        <div className="rounded-xl border border-border bg-card p-4 shadow-sm lg:col-span-2">
          <h3 className="mb-2 text-sm font-medium text-muted-foreground">Category split — legacy vs current</h3>
          {legacyQuery.isLoading ? (
            <Loading />
          ) : legacyQuery.error ? (
            <ErrorFallback error={legacyQuery.error} message="Could not load legacy split." onRetry={() => legacyQuery.refetch()} />
          ) : (legacyQuery.data?.byCategory ?? []).length === 0 ? (
            <p className="text-sm text-muted-foreground">No data.</p>
          ) : (
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={legacyQuery.data!.byCategory} margin={{ top: 10, right: 20, left: 0, bottom: 5 }}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="category" tick={{ fontSize: 12 }} />
                  <YAxis allowDecimals={false} />
                  <Tooltip />
                  <Legend />
                  <Bar dataKey="legacy" name="Imported (legacy)" stackId="split" fill={COLORS[4]} />
                  <Bar dataKey="current" name="Current" stackId="split" fill={COLORS[0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function SlaComplianceView({ data, mine }: { data: SlaCompliance; mine: boolean }) {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const [days, setDays] = useState(30)

  const trendQuery = useQuery<TrendPoint[]>({
    queryKey: ['reports', 'sla-trend', days, mine],
    queryFn: async () => {
      const qs = new URLSearchParams({ days: String(days), mine: String(mine) })
      const res = await fetchWithToken(instance, account!, `/api/v1/reports/sla-trend?${qs.toString()}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const nonBreached = Math.max(0, data.total - data.breached)
  const chartData = [
    { name: 'Compliant', value: nonBreached },
    { name: 'Breached', value: data.breached },
  ].filter((d) => d.value > 0)

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <MetricCard label="SLA Compliance" value={`${data.compliancePercent}%`} />
        <MetricCard label="Total SLAs" value={data.total} />
        <MetricCard label="Breached" value={data.breached} />
      </div>

      <div className="rounded-xl border border-border bg-card p-4 shadow-sm space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-medium text-muted-foreground">SLA compliance trend</h3>
          <select
            value={days}
            onChange={(e) => setDays(Number(e.target.value))}
            className="rounded-md border border-border bg-background px-2 py-1 text-sm"
          >
            <option value={7}>Last 7 days</option>
            <option value={30}>Last 30 days</option>
          </select>
        </div>
        {trendQuery.isLoading ? (
          <Loading />
        ) : trendQuery.error ? (
          <ErrorFallback error={trendQuery.error} message="Could not load SLA trend." onRetry={() => trendQuery.refetch()} />
        ) : (
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={trendQuery.data}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="date" tick={{ fontSize: 12 }} />
                <YAxis domain={[0, 100]} />
                <Tooltip />
                <Line type="monotone" dataKey="compliancePercent" stroke={COLORS[2]} strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>

      <div className="h-72 rounded-xl border border-border bg-card p-4 shadow-sm">
        <h3 className="mb-2 text-sm font-medium text-muted-foreground">SLA breaches</h3>
        <ResponsiveContainer width="100%" height="90%">
          <PieChart>
            <Pie data={chartData} dataKey="value" nameKey="name" outerRadius={80} label>
              {chartData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
            </Pie>
            <Tooltip />
            <Legend />
          </PieChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

function BreakdownBarChart({ title, query }: { title: string; query: { data?: AdHocQueryResponse; isLoading: boolean; error: Error | null; refetch: () => void } }) {
  const chartData = (query.data?.rows ?? [])
    .map((r) => ({ name: r.group ?? 'Unassigned', count: r.count }))
    .filter((d) => d.count > 0)

  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <h3 className="mb-2 text-sm font-medium text-muted-foreground">{title}</h3>
      {query.isLoading ? (
        <Loading />
      ) : query.error ? (
        <ErrorFallback error={query.error} message={`Could not load ${title.toLowerCase()}.`} onRetry={() => query.refetch()} />
      ) : chartData.length === 0 ? (
        <p className="text-sm text-muted-foreground">No data.</p>
      ) : (
        <div className="h-64">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chartData} margin={{ top: 10, right: 20, left: 0, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" tick={{ fontSize: 12 }} />
              <YAxis />
              <Tooltip />
              <Bar dataKey="count" name="Tickets" fill={COLORS[0]} maxBarSize={60} radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  )
}

function AgentWorkloadView({ data }: { data: AgentWorkload[] }) {
  if (data.length === 0) {
    return (
      <div className="h-96 rounded-xl border border-border bg-card p-4 shadow-sm flex items-center justify-center">
        <p className="text-sm text-muted-foreground">No open tickets currently assigned to agents.</p>
      </div>
    )
  }
  return (
    <div className="h-96 rounded-xl border border-border bg-card p-4 shadow-sm">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 20, right: 30, left: 0, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="agentName" tick={{ fontSize: 12 }} />
          <YAxis />
          <Tooltip />
          <Legend />
          <Bar dataKey="incidents" name="Incidents" stackId="work" fill={COLORS[0]} />
          <Bar dataKey="serviceRequests" name="Requests" stackId="work" fill={COLORS[1]} />
          <Bar dataKey="problems" name="Problems" stackId="work" fill={COLORS[4]} />
          <Bar dataKey="changes" name="Changes" stackId="work" fill={COLORS[3]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

function SprintVelocityView({ data }: { data: SprintVelocity[] }) {
  if (data.length === 0) {
    return (
      <div className="h-96 rounded-xl border border-border bg-card p-4 shadow-sm flex items-center justify-center">
        <p className="text-sm text-muted-foreground">No active or completed sprints with issues yet.</p>
      </div>
    )
  }
  return (
    <div className="h-96 rounded-xl border border-border bg-card p-4 shadow-sm">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 20, right: 30, left: 0, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="sprintName" tick={{ fontSize: 12 }} />
          <YAxis />
          <Tooltip />
          <Legend />
          <Bar dataKey="committedPoints" name="Committed (pts)" fill={COLORS[0]} />
          <Bar dataKey="completedPoints" name="Completed (pts)" fill={COLORS[2]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

function MetricCard({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="rounded-xl border border-border bg-card p-4 text-center shadow-sm">
      <p className="text-2xl font-bold">{value}</p>
      <p className="text-xs text-muted-foreground">{label}</p>
    </div>
  )
}

const TABS = [
  { key: 'tickets', label: 'Tickets Summary', endpoint: '/api/v1/reports/tickets-summary' },
  { key: 'sla', label: 'SLA Compliance', endpoint: '/api/v1/reports/sla-compliance' },
  { key: 'agent', label: 'Agent Workload', endpoint: '/api/v1/reports/agent-workload' },
  { key: 'worked', label: 'Worked Tickets', endpoint: '/api/v1/reports/agent-performance/tickets' },
  { key: 'sprint', label: 'Sprint Velocity', endpoint: '/api/v1/reports/sprint-velocity' },
  // Former "Standard Reports" page — consolidated here.
  { key: 'category', label: 'Incidents by Category', endpoint: '/api/v1/reports/incidents-by-category' },
  { key: 'catalog', label: 'Requests by Catalog', endpoint: '/api/v1/reports/requests-by-catalog' },
  { key: 'slaPriority', label: 'SLA by Priority', endpoint: '/api/v1/reports/sla-by-priority' },
  { key: 'approvals', label: 'Approval Backlog', endpoint: '/api/v1/reports/pending-approvals-backlog' },
]

/** Generic table for the fixed-dimension standard reports (merged in from the old Standard Reports page). */
function StandardTableView({ data }: { data: Record<string, unknown>[] }) {
  const columns = Object.keys(data[0] ?? {}).map((k) => ({
    key: k,
    header: k.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase()),
    render: (r: Record<string, unknown>) => String(r[k] ?? '—'),
  }))
  return (
    <DataTable<Record<string, unknown>>
      caption="Report results"
      columns={columns}
      data={data}
      getRowKey={(r) => JSON.stringify(r)}
      emptyText="No data."
    />
  )
}

export function ReportsDashboard() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const { currentUser } = useAuth()
  const isAdmin = currentUser?.roles.some((r) => r === 'ADMIN' || r === 'SUPER_ADMIN') ?? false
  const mine = !isAdmin
  const userId = currentUser?.id
  const [active, setActive] = useState('tickets')

  const allowedTabs = isAdmin ? TABS : TABS.filter((t) => t.key === 'tickets' || t.key === 'sla' || t.key === 'worked')
  const activeTab = allowedTabs.find((t) => t.key === active) ?? allowedTabs[0]

  const query = useQuery<unknown>({
    queryKey: ['report', activeTab.key, mine],
    queryFn: async () => {
      const sep = activeTab.endpoint.includes('?') ? '&' : '?'
      const res = await fetchWithToken(instance, account!, `${activeTab.endpoint}${sep}mine=${mine}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && activeTab.key !== 'worked',
    refetchInterval: 60_000,
    staleTime: 0,
  })

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-2xl font-semibold tracking-tight">{isAdmin ? 'Reporting Dashboards' : 'My Reporting'}</h1>
          {isAdmin && (
            <div className="flex gap-2">
              <Link to="/dashboard/reports/export" className="inline-flex items-center gap-2 rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-muted">
                <Download className="h-4 w-4" />
                Data Export
              </Link>
              <Link to="/dashboard/reports/query" className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90">
                <FileQuestion className="h-4 w-4" />
                Query Builder
              </Link>
            </div>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-2 border-b border-border pb-2">
          {allowedTabs.map((t) => (
            <button
              key={t.key}
              onClick={() => setActive(t.key)}
              className={`rounded-md px-4 py-2 text-sm font-medium transition ${
                active === t.key ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-muted'
              }`}
            >
              {t.label}
            </button>
          ))}
          {!!query.data && activeTab.key !== 'worked' && (
            <button
              onClick={() => downloadCsv(`${activeTab.key}-report.csv`, query.data)}
              className="ml-auto inline-flex items-center gap-1.5 rounded-md border border-border px-3 py-1.5 text-xs font-medium text-muted-foreground transition hover:bg-muted"
            >
              <Download className="h-3.5 w-3.5" />
              Export CSV
            </button>
          )}
        </div>

        {query.isLoading && <Loading />}
        {query.error && <ErrorFallback error={query.error} message="Could not load report." onRetry={() => query.refetch()} />}
        {!query.isLoading && !query.error && !!query.data && (
          <>
            {active === 'tickets' && <TicketsSummaryView data={query.data as TicketsSummary} mine={mine} userId={userId} />}
            {active === 'sla' && <SlaComplianceView data={query.data as SlaCompliance} mine={mine} />}
            {active === 'agent' && <AgentWorkloadView data={query.data as AgentWorkload[]} />}
            {active === 'sprint' && <SprintVelocityView data={query.data as SprintVelocity[]} />}
            {['category', 'catalog', 'slaPriority', 'approvals'].includes(active) && (
              <StandardTableView data={query.data as Record<string, unknown>[]} />
            )}
          </>
        )}
        {active === 'worked' && <WorkedTicketsView isAdmin={isAdmin} />}
      </div>
    </div>
  )
}

interface WorkedTicket {
  type: string
  id: string
  number: string
  title: string | null
  status: string
  createdAt: string | null
  resolvedAt: string | null
}

const WORKED_TYPE_LINK: Record<string, string> = {
  INCIDENT: '/dashboard/incidents',
  SERVICE_REQUEST: '/dashboard/service-requests',
  PROBLEM: '/dashboard/problems',
  CHANGE: '/dashboard/changes',
}

/** Part C: every ticket the agent worked, filterable by range/type/status. */
function WorkedTicketsView({ isAdmin }: { isAdmin: boolean }) {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [entityType, setEntityType] = useState('')
  const [status, setStatus] = useState('')
  const [agentId, setAgentId] = useState('')

  const usersQuery = useQuery<{ id: string; displayName: string }[]>({
    queryKey: ['worked-tickets', 'users'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/users')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && isAdmin,
  })

  const query = useQuery<WorkedTicket[]>({
    queryKey: ['worked-tickets', from, to, entityType, status, agentId],
    queryFn: async () => {
      const params = new URLSearchParams()
      if (from) params.set('from', new Date(from).toISOString())
      if (to) params.set('to', new Date(`${to}T23:59:59.999Z`).toISOString())
      if (entityType) params.set('entityType', entityType)
      if (status) params.set('status', status)
      if (agentId) params.set('agentId', agentId)
      const res = await fetchWithToken(instance, account!, `/api/v1/reports/agent-performance/tickets?${params}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const rows = query.data ?? []
  const selectCls = 'rounded-md border border-input bg-background px-2 py-1.5 text-sm outline-none focus:ring-2 focus:ring-ring'

  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <h3 className="text-sm font-medium text-muted-foreground">Worked Tickets</h3>
        <div className="ml-auto flex flex-wrap items-center gap-2">
          {isAdmin && (
            <select value={agentId} onChange={(e) => setAgentId(e.target.value)} className={selectCls}>
              <option value="">Me</option>
              {(usersQuery.data ?? []).map((u) => (
                <option key={u.id} value={u.id}>{u.displayName}</option>
              ))}
            </select>
          )}
          <select value={entityType} onChange={(e) => setEntityType(e.target.value)} className={selectCls}>
            <option value="">All types</option>
            <option value="incident">Incidents</option>
            <option value="service_request">Service Requests</option>
            <option value="problem">Problems</option>
            <option value="change">Changes</option>
          </select>
          <input
            value={status}
            onChange={(e) => setStatus(e.target.value.toUpperCase())}
            placeholder="Status (e.g. RESOLVED)"
            className={`${selectCls} w-44`}
          />
          <DateInput value={from} onChange={(e) => setFrom(e.target.value)} className="px-2 py-1.5" />
          <DateInput value={to} onChange={(e) => setTo(e.target.value)} className="px-2 py-1.5" />
          {rows.length > 0 && (
            <button
              onClick={() => downloadCsv('worked-tickets.csv', rows)}
              className="inline-flex items-center gap-1.5 rounded-md border border-border px-3 py-1.5 text-xs font-medium text-muted-foreground transition hover:bg-muted"
            >
              <Download className="h-3.5 w-3.5" />
              Export CSV
            </button>
          )}
        </div>
      </div>
      {query.isLoading ? (
        <Loading compact />
      ) : query.error ? (
        <p className="text-sm text-destructive">Could not load worked tickets.</p>
      ) : (
        <DataTable<WorkedTicket>
          caption="Tickets the agent worked on"
          columns={[
            { key: 'number', header: '#', render: (r) => (
              <Link to={`${WORKED_TYPE_LINK[r.type] ?? '/dashboard'}/${r.id}`} className="font-medium hover:underline">{r.number}</Link>
            ) },
            { key: 'type', header: 'Type', render: (r) => r.type.replace(/_/g, ' ') },
            { key: 'title', header: 'Title', render: (r) => r.title ?? '—' },
            { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            { key: 'createdAt', header: 'Created', render: (r) => formatDate(r.createdAt) },
            { key: 'resolvedAt', header: 'Resolved', render: (r) => r.resolvedAt ? formatDateTime(r.resolvedAt) : '—' },
          ]}
          data={rows}
          getRowKey={(r) => r.id}
          emptyText="No worked tickets match the filters."
        />
      )}
    </div>
  )
}
