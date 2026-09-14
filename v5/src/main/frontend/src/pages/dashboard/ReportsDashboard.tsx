import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { FileQuestion, FolderOpen } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'

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
  { key: 'sprint', label: 'Sprint Velocity', endpoint: '/api/v1/reports/sprint-velocity' },
]

export function ReportsDashboard() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const { currentUser } = useAuth()
  const isAdmin = currentUser?.roles.some((r) => r === 'ADMIN' || r === 'SUPER_ADMIN') ?? false
  const mine = !isAdmin
  const userId = currentUser?.id
  const [active, setActive] = useState('tickets')

  const allowedTabs = isAdmin ? TABS : TABS.filter((t) => t.key === 'tickets' || t.key === 'sla')
  const activeTab = allowedTabs.find((t) => t.key === active) ?? allowedTabs[0]

  const query = useQuery<unknown>({
    queryKey: ['report', activeTab.key, mine],
    queryFn: async () => {
      const sep = activeTab.endpoint.includes('?') ? '&' : '?'
      const res = await fetchWithToken(instance, account!, `${activeTab.endpoint}${sep}mine=${mine}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
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
              <Link to="/dashboard/reports/standard" className="inline-flex items-center gap-2 rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-muted">
                <FileQuestion className="h-4 w-4" />
                Standard Reports
              </Link>
              <Link to="/dashboard/reports/saved" className="inline-flex items-center gap-2 rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-muted">
                <FolderOpen className="h-4 w-4" />
                Saved Reports
              </Link>
              <Link to="/dashboard/reports/query" className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90">
                <FileQuestion className="h-4 w-4" />
                Query Builder
              </Link>
            </div>
          )}
        </div>

        <div className="flex flex-wrap gap-2 border-b border-border pb-2">
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
        </div>

        {query.isLoading && <Loading />}
        {query.error && <ErrorFallback error={query.error} message="Could not load report." onRetry={() => query.refetch()} />}
        {!query.isLoading && !query.error && !!query.data && (
          <>
            {active === 'tickets' && <TicketsSummaryView data={query.data as TicketsSummary} mine={mine} userId={userId} />}
            {active === 'sla' && <SlaComplianceView data={query.data as SlaCompliance} mine={mine} />}
            {active === 'agent' && <AgentWorkloadView data={query.data as AgentWorkload[]} />}
            {active === 'sprint' && <SprintVelocityView data={query.data as SprintVelocity[]} />}
          </>
        )}
      </div>
    </div>
  )
}
