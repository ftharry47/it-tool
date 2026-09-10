import { Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { ArrowUpRight, RotateCcw } from 'lucide-react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { DataTable } from '../../components/ui/DataTable'
import { DateInput } from '../../components/ui/DateInput'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { Loading } from '../../components/ui/Loading'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { SlaCountdown } from '../../components/ui/SlaCountdown'
import { formatDate, formatDateTime } from '../../lib/date'

function forwardWheelToMain(e: React.WheelEvent<HTMLDivElement>) {
  const el = e.currentTarget
  if (e.deltaY === 0) return
  if (el.scrollHeight > el.clientHeight) return
  const scrollParent = el.closest('main')
  if (scrollParent && scrollParent !== el) {
    e.preventDefault()
    scrollParent.scrollBy({ top: e.deltaY })
  }
}

const COLORS = [
  'var(--chart-1)',
  'var(--chart-3)',
  'var(--chart-2)',
  'var(--chart-4)',
  'var(--chart-5)',
  'var(--chart-6)',
]

interface SlaCompliance {
  total: number
  breached: number
  compliancePercent: number
}

interface TicketsSummary {
  total: number
  open: number
  inProgress: number
  resolvedToday: number
  unassigned: number
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

interface IncidentSummary {
  id: string
  number: number
  title: string
  status: string
  priority: string | null
  category: string | null
  requester: string | null
  assignee: string | null
  location: string | null
  phone: string | null
  createdAt: string
  slaBreachStatus: string | null
  responseDueAt: string | null
  resolutionDueAt: string | null
  responseMetAt: string | null
  resolutionMetAt: string | null
}

interface ProblemSummary {
  id: string
  number: string
  title: string
  status: string
  assigneeName: string | null
  createdAt: string
}

interface ChangeSummary {
  id: string
  number: string
  title: string
  changeType: string
  risk: string
  status: string
  assigneeName: string | null
  plannedStart: string | null
  plannedEnd: string | null
  createdAt: string
}

interface AgentWorkload {
  agentName: string
  openCount: number
  onTrack: number
  atRisk: number
  breached: number
  noSla: number
}

interface TicketsByLocationRow {
  locationId: string
  locationName: string
  totalOpen: number
  openIncidents: number
  openServiceRequests: number
  oldestOpenDays: number | null
  resolvedCount: number
  breachedCount: number
}

interface EscalationEntry {
  incidentId: string
  incidentNumber: number | null
  incidentTitle: string | null
  action: string
  actorName: string
  detail: string
  createdAt: string
}

interface AgentPerformanceReport {
  agentId: string
  agentName: string
  period: string
  ticketsHandled: number
  ticketsResolved: number
  resolutionRate: number
  slaCompliancePct: number
  slaEvaluated: number
  breachCount: number
  reopenedCount: number
  avgResolutionMinutes: number
  autoEscalationsAway: number
  manualSelfEscalations: number
  score: number
  grade: string
}

interface MyPerformanceResponse {
  report: AgentPerformanceReport
  rollingSlaCompliancePct: number
}

interface SavedPerfReport {
  id: string
  name: string
  payload: string | null
  createdAt: string
}

interface MonthlySla {
  month: string
  total: number
  breached: number
  compliancePercent: number
}

interface MyTask {
  taskId: string
  description: string
  sequenceOrder: number
  status: string
  expectedDeliveryDate: string | null
  serviceRequestId: string
  serviceRequestNumber: string
  catalogItemName: string | null
  requesterName: string | null
  assignedAt: string | null
}

// Fixed team IDs seeded by migrations — same constants as IncidentService.
const TIER_TEAMS: { id: string; label: string }[] = [
  { id: '00000000-0000-0000-0000-000000000020', label: 'L1 Support' },
  { id: '00000000-0000-0000-0000-000000000021', label: 'L2 Support' },
  { id: '00000000-0000-0000-0000-000000000022', label: 'L3 Support' },
]
const IT_FULFILLMENT_TEAM_ID = '00000000-0000-0000-0000-000000000010'

const SLA_RANK: Record<string, number> = { BREACHED: 0, AT_RISK: 1, ON_TRACK: 2 }
const PRIORITY_RANK: Record<string, number> = { P1: 0, Critical: 0, P2: 1, High: 1, P3: 2, Medium: 2, P4: 3, Low: 3 }

function sortByUrgency(incidents: IncidentSummary[]): IncidentSummary[] {
  return [...incidents].sort((a, b) => {
    const rankA = a.slaBreachStatus != null ? (SLA_RANK[a.slaBreachStatus] ?? 3) : 3
    const rankB = b.slaBreachStatus != null ? (SLA_RANK[b.slaBreachStatus] ?? 3) : 3
    if (rankA !== rankB) return rankA - rankB
    const pa = a.priority != null ? (PRIORITY_RANK[a.priority] ?? 4) : 4
    const pb = b.priority != null ? (PRIORITY_RANK[b.priority] ?? 4) : 4
    if (pa !== pb) return pa - pb
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  })
}

/** Decode the JSON payload of generated AGENT_PERFORMANCE saved reports. */
function parsePerfReports(saved: SavedPerfReport[] | undefined): AgentPerformanceReport[] {
  if (!saved) return []
  return saved
    .map((s) => {
      try {
        return s.payload ? (JSON.parse(s.payload) as AgentPerformanceReport) : null
      } catch {
        return null
      }
    })
    .filter((r): r is AgentPerformanceReport => r != null)
}

const GRADE_COLORS: Record<string, string> = {
  A: 'bg-green-500/15 text-green-600 dark:text-green-400',
  B: 'bg-blue-500/15 text-blue-600 dark:text-blue-400',
  C: 'bg-yellow-500/15 text-yellow-600 dark:text-yellow-400',
  D: 'bg-orange-500/15 text-orange-600 dark:text-orange-400',
  F: 'bg-red-500/15 text-red-600 dark:text-red-400',
}

function GradeChip({ grade }: { grade: string }) {
  return (
    <span className={`inline-flex h-8 w-8 items-center justify-center rounded-md text-sm font-bold ${GRADE_COLORS[grade] ?? 'bg-muted text-muted-foreground'}`}>
      {grade}
    </span>
  )
}

/** Part B: live current-month performance for the signed-in staff member. */
function MyPerformanceCard({ data, isLoading }: { data: MyPerformanceResponse | undefined; isLoading: boolean }) {
  if (isLoading) {
    return (
      <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
        <p className="text-sm text-muted-foreground">Loading your monthly stats…</p>
      </div>
    )
  }
  if (!data) return null
  const r = data.report
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-muted-foreground">This Month — Your Performance</h3>
        <GradeChip grade={r.grade} />
      </div>
      <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div>
          <p className="text-2xl font-bold">{r.ticketsHandled}</p>
          <p className="text-xs text-muted-foreground">Handled</p>
        </div>
        <div>
          <p className="text-2xl font-bold">{r.ticketsResolved}</p>
          <p className="text-xs text-muted-foreground">Resolved</p>
        </div>
        <div>
          <p className="text-2xl font-bold">{data.rollingSlaCompliancePct >= 0 ? `${data.rollingSlaCompliancePct}%` : '—'}</p>
          <p className="text-xs text-muted-foreground">SLA (all-time)</p>
        </div>
        <div>
          <p className="text-2xl font-bold">{r.score}</p>
          <p className="text-xs text-muted-foreground">Score / 100</p>
        </div>
      </div>
    </div>
  )
}

/** Part D: SUPER_ADMIN — top 5 agents by score from the latest monthly reports. */
function TopPerformers({ reports }: { reports: AgentPerformanceReport[] }) {
  const top = [...reports].sort((a, b) => b.score - a.score).slice(0, 5)
  if (top.length === 0) return null
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <h3 className="mb-3 text-sm font-medium text-muted-foreground">Top Performers — {top[0].period}</h3>
      <ul className="space-y-2">
        {top.map((r, i) => (
          <li key={r.agentId} className="flex items-center justify-between text-sm">
            <span className="flex items-center gap-2">
              <span className="w-5 text-muted-foreground">{i + 1}.</span>
              <span className="font-medium">{r.agentName}</span>
            </span>
            <span className="flex items-center gap-2">
              <span className="text-muted-foreground">{r.score}</span>
              <GradeChip grade={r.grade} />
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Part D: SUPER_ADMIN — SLA compliance delta, this month vs last month. */
function SlaTrendDelta({ data }: { data: MonthlySla[] | undefined }) {
  if (!data || data.length < 2) return null
  const prev = data[data.length - 2]
  const curr = data[data.length - 1]
  const delta = Math.round((curr.compliancePercent - prev.compliancePercent) * 10) / 10
  const up = delta >= 0
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <h3 className="text-sm font-medium text-muted-foreground">SLA Trend</h3>
      <div className="mt-2 flex items-baseline gap-2">
        <p className="text-2xl font-bold">{curr.compliancePercent}%</p>
        <span className={`text-sm font-medium ${up ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
          {up ? '▲' : '▼'} {Math.abs(delta)}% vs {prev.month}
        </span>
      </div>
      <p className="mt-1 text-xs text-muted-foreground">{curr.breached} of {curr.total} breached this month</p>
    </div>
  )
}

/** Part D: SUPER_ADMIN — all agents' latest monthly reports side-by-side. */
function AgentPerformanceTable({ reports, isLoading }: { reports: AgentPerformanceReport[]; isLoading: boolean }) {
  if (isLoading) {
    return (
      <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
        <p className="text-sm text-muted-foreground">Loading agent performance…</p>
      </div>
    )
  }
  if (reports.length === 0) {
    return (
      <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
        <h3 className="text-sm font-medium text-muted-foreground">Agent Performance — Monthly Reports</h3>
        <p className="mt-2 text-sm text-muted-foreground">No generated reports yet. Reports are created automatically on the 1st of each month for agents who handled tickets.</p>
      </div>
    )
  }
  const sorted = [...reports].sort((a, b) => b.score - a.score)
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <h3 className="mb-3 text-sm font-medium text-muted-foreground">Agent Performance — {sorted[0].period}</h3>
      <div className="overflow-x-auto scrollbar-themed" onWheel={forwardWheelToMain}>
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border text-left text-xs text-muted-foreground">
              <th className="pb-2 pr-4 font-medium">Agent</th>
              <th className="pb-2 pr-4 font-medium">Handled</th>
              <th className="pb-2 pr-4 font-medium">Resolved</th>
              <th className="pb-2 pr-4 font-medium">SLA %</th>
              <th className="pb-2 pr-4 font-medium">Breaches</th>
              <th className="pb-2 pr-4 font-medium">Reopens</th>
              <th className="pb-2 pr-4 font-medium">Auto-Esc.</th>
              <th className="pb-2 pr-4 font-medium">Score</th>
              <th className="pb-2 font-medium">Grade</th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((r) => (
              <tr key={r.agentId} className="border-b border-border/50 last:border-0">
                <td className="py-2 pr-4 font-medium">{r.agentName}</td>
                <td className="py-2 pr-4">{r.ticketsHandled}</td>
                <td className="py-2 pr-4">{r.ticketsResolved}</td>
                <td className="py-2 pr-4">{r.slaCompliancePct >= 0 ? `${r.slaCompliancePct}%` : '—'}</td>
                <td className="py-2 pr-4">{r.breachCount}</td>
                <td className="py-2 pr-4">{r.reopenedCount}</td>
                <td className="py-2 pr-4">{r.autoEscalationsAway}</td>
                <td className="py-2 pr-4 font-semibold">{r.score}</td>
                <td className="py-2"><GradeChip grade={r.grade} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function MetricCard({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="rounded-xl border border-border bg-card p-4 text-center shadow-sm transition-shadow duration-150 hover:shadow-md">
      <p className="text-2xl font-bold">{value}</p>
      <p className="text-xs text-muted-foreground">{label}</p>
    </div>
  )
}

const ACTIVE_STATUSES = 'NEW,IN_PROGRESS,ON_HOLD,REOPENED'

function SlaView({ data }: { data: SlaCompliance }) {
  if (data.total === 0) {
    return (
      <div className="space-y-4 rounded-xl border border-border bg-card p-4 shadow-sm">
        <h3 className="text-sm font-medium text-muted-foreground">SLA Compliance</h3>
        <p className="text-sm text-muted-foreground">
          No SLA data yet. Once incidents have SLA policies, compliance will appear here.
        </p>
      </div>
    )
  }
  const nonBreached = Math.max(0, data.total - data.breached)
  const chartData = [
    { name: 'Compliant', value: nonBreached },
    { name: 'Breached', value: data.breached },
  ].filter((d) => d.value > 0)
  return (
    <div className="space-y-4 rounded-xl border border-border bg-card p-4 shadow-sm">
      <h3 className="text-sm font-medium text-muted-foreground">SLA Compliance</h3>
      <div className="grid grid-cols-3 gap-4">
        <MetricCard label="Compliance" value={`${data.compliancePercent}%`} />
        <MetricCard label="Total" value={data.total} />
        <MetricCard label="Breached" value={data.breached} />
      </div>
      <div className="h-56">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={chartData} dataKey="value" nameKey="name" outerRadius={70} label>
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

function TicketsView({ data }: { data: TicketsSummary }) {
  const closed = Math.max(0, data.total - data.open - data.inProgress - data.resolvedToday)
  const chartData = [
    { name: 'Open', value: data.open },
    { name: 'In Progress', value: data.inProgress },
    { name: 'Resolved Today', value: data.resolvedToday },
    { name: 'Closed', value: closed },
  ].filter((d) => d.value > 0)
  return (
    <div className="space-y-4 rounded-xl border border-border bg-card p-4 shadow-sm">
      <h3 className="text-sm font-medium text-muted-foreground">Ticket Queue</h3>
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <MetricCard label="Total" value={data.total} />
        <MetricCard label="Open" value={data.open} />
        <MetricCard label="In Progress" value={data.inProgress} />
        <MetricCard label="Resolved Today" value={data.resolvedToday} />
      </div>
      <div className="h-56">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={chartData} dataKey="value" nameKey="name" outerRadius={70} label>
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

function PriorityView({ data, isLoading, error, onRetry }: { data: AdHocQueryResponse | undefined; isLoading: boolean; error: Error | null; onRetry: () => void }) {
  if (isLoading) return <Loading compact />
  if (error) return <ErrorFallback error={error} message="Could not load priority breakdown." onRetry={onRetry} />
  if (!data) return <Loading compact />
  const chartData = data.rows.map((row) => ({ name: row.group ?? 'Unassigned', count: row.count })).filter((d) => d.count > 0)
  if (chartData.length === 0) {
    return <p className="text-sm text-muted-foreground">No open incidents by priority.</p>
  }
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <h3 className="mb-2 text-sm font-medium text-muted-foreground">Open incidents by priority</h3>
      <div className="h-64">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={chartData} margin={{ top: 10, right: 20, left: 0, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="name" tick={{ fontSize: 12 }} />
            <YAxis />
            <Tooltip />
            <Bar dataKey="count" name="Incidents" fill={COLORS[0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

const INCIDENT_COLUMNS = [
  { key: 'number', header: '#' },
  { key: 'title', header: 'Title', render: (row: IncidentSummary) => <Link to={`/dashboard/incidents/${row.id}`} className="font-medium hover:underline">{row.title}</Link> },
  { key: 'status', header: 'Status', render: (row: IncidentSummary) => <StatusBadge status={row.status} /> },
  { key: 'priority', header: 'Priority' },
  { key: 'assignee', header: 'Assignee' },
  { key: 'sla', header: 'SLA', render: (row: IncidentSummary) => (
    <SlaCountdown
      breachStatus={row.slaBreachStatus}
      resolutionDueAt={row.resolutionDueAt}
      resolutionMetAt={row.resolutionMetAt}
      createdAt={row.createdAt}
    />
  ) },
  { key: 'createdAt', header: 'Created', render: (row: IncidentSummary) => formatDate(row.createdAt) },
]

const SLA_SEGMENT_COLORS: Record<string, string> = {
  onTrack: '#22c55e',
  atRisk: '#eab308',
  breached: '#ef4444',
  noSla: '#94a3b8',
}

function WorkloadChart({ data, isLoading }: { data: AgentWorkload[] | undefined; isLoading: boolean }) {
  if (isLoading) return <Loading compact />
  const rows = (data ?? []).filter((r) => r.openCount > 0)
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <h3 className="mb-2 text-sm font-medium text-muted-foreground">Workload per Agent</h3>
      {rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">No open assigned incidents.</p>
      ) : (
        <div style={{ height: Math.max(160, rows.length * 44) }}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={rows} layout="vertical" margin={{ top: 5, right: 20, left: 10, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" horizontal={false} />
              <XAxis type="number" allowDecimals={false} />
              <YAxis type="category" dataKey="agentName" width={140} tick={{ fontSize: 12 }} />
              <Tooltip />
              <Legend />
              <Bar dataKey="onTrack" name="On Track" stackId="sla" fill={SLA_SEGMENT_COLORS.onTrack} />
              <Bar dataKey="atRisk" name="At Risk" stackId="sla" fill={SLA_SEGMENT_COLORS.atRisk} />
              <Bar dataKey="breached" name="Breached" stackId="sla" fill={SLA_SEGMENT_COLORS.breached} />
              <Bar dataKey="noSla" name="No SLA" stackId="sla" fill={SLA_SEGMENT_COLORS.noSla} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  )
}

function EscalationsFeed({ data, isLoading }: { data: EscalationEntry[] | undefined; isLoading: boolean }) {
  if (isLoading) return <Loading compact />
  const entries = data ?? []
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <h3 className="mb-3 text-sm font-medium text-muted-foreground">Recent Escalations</h3>
      {entries.length === 0 ? (
        <p className="text-sm text-muted-foreground">No recent escalations or reopens.</p>
      ) : (
        <ul className="space-y-3">
          {entries.map((e, i) => (
            <li key={`${e.incidentId}-${i}`} className="flex items-start gap-3 text-sm">
              <span className="mt-0.5 shrink-0 text-muted-foreground">
                {e.action === 'REOPEN' ? <RotateCcw className="h-4 w-4" /> : <ArrowUpRight className="h-4 w-4" />}
              </span>
              <div className="min-w-0">
                <Link to={`/dashboard/incidents/${e.incidentId}`} className="font-medium hover:underline">
                  {e.incidentNumber != null ? `INC-${e.incidentNumber}` : 'Incident'}
                </Link>
                <span className="text-muted-foreground"> {e.incidentTitle ? `· ${e.incidentTitle}` : ''}</span>
                <p className="text-xs text-muted-foreground">
                  {e.detail} · by {e.actorName} · {formatDateTime(e.createdAt)}
                </p>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function FulfillmentTasks({ data, isLoading }: { data: MyTask[] | undefined; isLoading: boolean }) {
  if (isLoading) return <Loading compact />
  const tasks = data ?? []
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <h3 className="mb-3 text-sm font-medium text-muted-foreground">IT Fulfillment — Pending Tasks</h3>
      {tasks.length === 0 ? (
        <p className="text-sm text-muted-foreground">No pending fulfillment tasks assigned to you.</p>
      ) : (
        <ul className="space-y-2">
          {tasks.map((t) => (
            <li key={t.taskId} className="flex items-center justify-between gap-3 text-sm">
              <div className="min-w-0">
                <Link to={`/dashboard/service-requests/${t.serviceRequestId}`} className="font-medium hover:underline">
                  {t.serviceRequestNumber}
                </Link>
                <span className="text-muted-foreground"> · {t.description}</span>
                {t.catalogItemName && <span className="text-xs text-muted-foreground"> ({t.catalogItemName})</span>}
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <StatusBadge status={t.status} />
                {t.expectedDeliveryDate && (
                  <span className="text-xs text-muted-foreground">Due {formatDate(t.expectedDeliveryDate)}</span>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function IncidentSection({ title, caption, data, isLoading, emptyText }: {
  title: string
  caption: string
  data: IncidentSummary[] | undefined
  isLoading: boolean
  emptyText: string
}) {
  return (
    <div className="space-y-2">
      <h3 className="text-sm font-medium text-muted-foreground">{title}</h3>
      <DataTable<IncidentSummary>
        caption={caption}
        columns={INCIDENT_COLUMNS}
        data={data ?? []}
        getRowKey={(row) => row.id}
        isLoading={isLoading}
        emptyText={emptyText}
      />
    </div>
  )
}

const PROBLEM_COLUMNS = [
  { key: 'number', header: '#' },
  { key: 'title', header: 'Title', render: (row: ProblemSummary) => <Link to={`/dashboard/problems/${row.id}`} className="font-medium hover:underline">{row.title}</Link> },
  { key: 'status', header: 'Status', render: (row: ProblemSummary) => <StatusBadge status={row.status} /> },
  { key: 'assignee', header: 'Assignee', render: (row: ProblemSummary) => row.assigneeName ?? 'Unassigned' },
  { key: 'createdAt', header: 'Created', render: (row: ProblemSummary) => formatDate(row.createdAt) },
]

const CHANGE_COLUMNS = [
  { key: 'number', header: '#' },
  { key: 'title', header: 'Title', render: (row: ChangeSummary) => <Link to={`/dashboard/changes/${row.id}`} className="font-medium hover:underline">{row.title}</Link> },
  { key: 'status', header: 'Status', render: (row: ChangeSummary) => <StatusBadge status={row.status} /> },
  { key: 'changeType', header: 'Type' },
  { key: 'risk', header: 'Risk' },
  { key: 'plannedStart', header: 'Planned start', render: (row: ChangeSummary) => row.plannedStart ? formatDate(row.plannedStart) : '—' },
  { key: 'plannedEnd', header: 'Planned end', render: (row: ChangeSummary) => row.plannedEnd ? formatDate(row.plannedEnd) : '—' },
  { key: 'assignee', header: 'Assignee', render: (row: ChangeSummary) => row.assigneeName ?? 'Unassigned' },
]

const OPEN_PROBLEM_STATUSES = new Set(['NEW', 'INVESTIGATING', 'KNOWN_ERROR'])
const OPEN_CHANGE_STATUSES = new Set(['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'SCHEDULED', 'IN_PROGRESS'])
const CHANGE_RISK_ORDER: Record<string, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 }

function sortProblems(data: ProblemSummary[] | undefined): ProblemSummary[] {
  const list = data ?? []
  return [...list].sort((a, b) => {
    const aOpen = OPEN_PROBLEM_STATUSES.has(a.status)
    const bOpen = OPEN_PROBLEM_STATUSES.has(b.status)
    if (aOpen !== bOpen) return aOpen ? -1 : 1
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  })
}

function sortChanges(data: ChangeSummary[] | undefined): ChangeSummary[] {
  const list = data ?? []
  return [...list].sort((a, b) => {
    const aOpen = OPEN_CHANGE_STATUSES.has(a.status)
    const bOpen = OPEN_CHANGE_STATUSES.has(b.status)
    if (aOpen !== bOpen) return aOpen ? -1 : 1
    const aRisk = CHANGE_RISK_ORDER[a.risk] ?? 3
    const bRisk = CHANGE_RISK_ORDER[b.risk] ?? 3
    if (aRisk !== bRisk) return aRisk - bRisk
    const aStart = a.plannedStart ? new Date(a.plannedStart).getTime() : Number.POSITIVE_INFINITY
    const bStart = b.plannedStart ? new Date(b.plannedStart).getTime() : Number.POSITIVE_INFINITY
    return aStart - bStart
  })
}

function ProblemSection({ title, caption, data, isLoading, emptyText }: {
  title: string
  caption: string
  data: ProblemSummary[] | undefined
  isLoading: boolean
  emptyText: string
}) {
  return (
    <div className="space-y-2">
      <h3 className="text-sm font-medium text-muted-foreground">{title}</h3>
      <DataTable<ProblemSummary>
        caption={caption}
        columns={PROBLEM_COLUMNS}
        data={data ?? []}
        getRowKey={(row) => row.id}
        isLoading={isLoading}
        emptyText={emptyText}
      />
    </div>
  )
}

function ChangeSection({ title, caption, data, isLoading, emptyText }: {
  title: string
  caption: string
  data: ChangeSummary[] | undefined
  isLoading: boolean
  emptyText: string
}) {
  return (
    <div className="space-y-2">
      <h3 className="text-sm font-medium text-muted-foreground">{title}</h3>
      <DataTable<ChangeSummary>
        caption={caption}
        columns={CHANGE_COLUMNS}
        data={data ?? []}
        getRowKey={(row) => row.id}
        isLoading={isLoading}
        emptyText={emptyText}
      />
    </div>
  )
}

export function Dashboard() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const { currentUser } = useAuth()

  const [locationFrom, setLocationFrom] = useState('')
  const [locationTo, setLocationTo] = useState('')
  const [locationStatus, setLocationStatus] = useState('OPEN')

  const isAdmin = !!currentUser?.roles.some((r) => ['ADMIN', 'SUPER_ADMIN'].includes(r))
  const isStaff = !!currentUser?.roles.some((r) => ['AGENT', 'TEAM_LEAD', 'ADMIN', 'SUPER_ADMIN'].includes(r))
  const myTierTeams = TIER_TEAMS.filter((t) => currentUser?.teamIds?.includes(t.id))
  const isFulfillmentMember = !!currentUser?.teamIds?.includes(IT_FULFILLMENT_TEAM_ID)

  const slaQuery = useQuery<SlaCompliance>({
    queryKey: ['dashboard', 'sla-compliance'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/reports/sla-compliance')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && isAdmin,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const ticketsQuery = useQuery<TicketsSummary>({
    queryKey: ['dashboard', 'tickets-summary'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/reports/tickets-summary')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && isAdmin,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const priorityQuery = useQuery<AdHocQueryResponse>({
    queryKey: ['dashboard', 'priority-breakdown'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/reports/priority-breakdown')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && isAdmin,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const workloadQuery = useQuery<AgentWorkload[]>({
    queryKey: ['dashboard', 'agent-workload'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/reports/agent-workload')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && isAdmin,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const escalationsQuery = useQuery<EscalationEntry[]>({
    queryKey: ['dashboard', 'escalations'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/incidents/escalations?limit=10')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && isAdmin,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const allActiveQuery = useQuery<IncidentSummary[]>({
    queryKey: ['dashboard', 'all-active'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, `/api/v1/incidents?status=${ACTIVE_STATUSES}&limit=20`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && isAdmin,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const myTicketsQuery = useQuery<IncidentSummary[]>({
    queryKey: ['dashboard', 'my-tickets', currentUser?.id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, `/api/v1/incidents?status=${ACTIVE_STATUSES}&assigneeId=${currentUser!.id}&limit=50`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!currentUser,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const isSuperAdmin = !!currentUser?.roles.includes('SUPER_ADMIN')

  const myProblemsQuery = useQuery<ProblemSummary[]>({
    queryKey: ['dashboard', 'my-problems', currentUser?.id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/problems?mine=true')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!currentUser && isStaff,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const myChangesQuery = useQuery<ChangeSummary[]>({
    queryKey: ['dashboard', 'my-changes', currentUser?.id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/changes?mine=true')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!currentUser && isStaff,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  // Part B: live current-month performance for the signed-in staff member.
  const myPerfQuery = useQuery<MyPerformanceResponse>({
    queryKey: ['dashboard', 'my-performance', currentUser?.id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/reports/agent-performance/me')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!currentUser,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  // Part D: SUPER_ADMIN aggregate — all generated monthly performance reports.
  const allPerfQuery = useQuery<SavedPerfReport[]>({
    queryKey: ['dashboard', 'agent-performance-all'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/reports/agent-performance/all')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && isSuperAdmin,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  // SLA trend delta: this month vs last month compliance.
  const slaTrendQuery = useQuery<MonthlySla[]>({
    queryKey: ['dashboard', 'sla-trend-monthly'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/reports/sla-trend/monthly?months=2')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && isSuperAdmin,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const myTasksQuery = useQuery<MyTask[]>({
    queryKey: ['dashboard', 'my-tasks', currentUser?.id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/service-requests/my-tasks')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!currentUser && isFulfillmentMember,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const ticketsByLocationQuery = useQuery<TicketsByLocationRow[]>({
    queryKey: ['dashboard', 'tickets-by-location', locationStatus, locationFrom, locationTo],
    queryFn: async () => {
      const params = new URLSearchParams()
      params.set('status', locationStatus || 'OPEN')
      if (locationFrom) params.set('from', new Date(locationFrom).toISOString())
      if (locationTo) params.set('to', new Date(`${locationTo}T23:59:59.999Z`).toISOString())
      const res = await fetchWithToken(instance, account, `/api/v1/reports/tickets-by-location?${params}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && isAdmin,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  if (!currentUser) {
    return (
      <div className="flex h-screen items-center justify-center bg-background">
        <Loading message="Loading your dashboard…" />
      </div>
    )
  }

  const isLoading = isAdmin && (slaQuery.isLoading || ticketsQuery.isLoading)
  const hasError = isAdmin && (slaQuery.error || ticketsQuery.error)

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-7xl space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
            <p className="text-sm text-muted-foreground">Welcome back, {currentUser.displayName ?? currentUser.email}.</p>
          </div>
        </div>

        {isLoading && <Loading />}
        {hasError && <ErrorFallback error={slaQuery.error ?? ticketsQuery.error} message="Could not load dashboard metrics." onRetry={() => { slaQuery.refetch(); ticketsQuery.refetch() }} />}

        {!isLoading && !hasError && (
          <div className="space-y-6">
            {isAdmin && (
              <>
                <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                  <MetricCard label="Open" value={ticketsQuery.data?.open ?? 0} />
                  <MetricCard label="Breached SLAs" value={slaQuery.data?.breached ?? 0} />
                  <MetricCard label="Unassigned" value={ticketsQuery.data?.unassigned ?? 0} />
                  <MetricCard label="Resolved today" value={ticketsQuery.data?.resolvedToday ?? 0} />
                </div>

                <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                  {slaQuery.data && <SlaView data={slaQuery.data} />}
                  {ticketsQuery.data && <TicketsView data={ticketsQuery.data} />}
                </div>

                <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                  <WorkloadChart data={workloadQuery.data} isLoading={workloadQuery.isLoading} />
                  <EscalationsFeed data={escalationsQuery.data} isLoading={escalationsQuery.isLoading} />
                </div>

                <PriorityView data={priorityQuery.data} isLoading={priorityQuery.isLoading} error={priorityQuery.error} onRetry={() => priorityQuery.refetch()} />

                <TicketsByLocationSection
                  data={ticketsByLocationQuery.data}
                  isLoading={ticketsByLocationQuery.isLoading}
                  error={ticketsByLocationQuery.error}
                  status={locationStatus}
                  from={locationFrom}
                  to={locationTo}
                  onStatusChange={setLocationStatus}
                  onFromChange={setLocationFrom}
                  onToChange={setLocationTo}
                  onRetry={() => ticketsByLocationQuery.refetch()}
                />

                {isSuperAdmin && (
                  <>
                    <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                      <TopPerformers reports={parsePerfReports(allPerfQuery.data)} />
                      <SlaTrendDelta data={slaTrendQuery.data} />
                    </div>
                    <AgentPerformanceTable reports={parsePerfReports(allPerfQuery.data)} isLoading={allPerfQuery.isLoading} />
                  </>
                )}

                <IncidentSection
                  title="All Active Incidents"
                  caption="All active incidents across the organization"
                  data={allActiveQuery.data}
                  isLoading={allActiveQuery.isLoading}
                  emptyText="No active incidents."
                />
              </>
            )}

            <MyPerformanceCard data={myPerfQuery.data} isLoading={myPerfQuery.isLoading} />

            <IncidentSection
              title="My Tickets"
              caption="Incidents assigned to you, sorted by urgency"
              data={sortByUrgency(myTicketsQuery.data ?? [])}
              isLoading={myTicketsQuery.isLoading}
              emptyText="No incidents assigned to you."
            />

            {isStaff && (
              <>
                <ProblemSection
                  title="My Problems"
                  caption="Problems assigned to you, open items first"
                  data={sortProblems(myProblemsQuery.data)}
                  isLoading={myProblemsQuery.isLoading}
                  emptyText="No problems assigned to you."
                />

                <ChangeSection
                  title="My Changes"
                  caption="Changes assigned to you, open and highest-risk items first"
                  data={sortChanges(myChangesQuery.data)}
                  isLoading={myChangesQuery.isLoading}
                  emptyText="No changes assigned to you."
                />
              </>
            )}

            {myTierTeams.map((tier) => (
              <TierQueue key={tier.id} tier={tier} instance={instance} account={account} />
            ))}

            {isFulfillmentMember && (
              <FulfillmentTasks data={myTasksQuery.data} isLoading={myTasksQuery.isLoading} />
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function TicketsByLocationSection({
  data,
  isLoading,
  error,
  status,
  from,
  to,
  onStatusChange,
  onFromChange,
  onToChange,
  onRetry,
}: {
  data: TicketsByLocationRow[] | undefined
  isLoading: boolean
  error: Error | null
  status: string
  from: string
  to: string
  onStatusChange: (status: string) => void
  onFromChange: (from: string) => void
  onToChange: (to: string) => void
  onRetry: () => void
}) {
  const rows = data ?? []

  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h3 className="text-sm font-medium text-muted-foreground">Tickets by Location</h3>
        <div className="flex flex-wrap items-center gap-2">
          <select
            value={status}
            onChange={(e) => onStatusChange(e.target.value)}
            className="rounded-md border border-input bg-background px-2 py-1 text-sm outline-none focus:ring-2 focus:ring-ring"
          >
            <option value="OPEN">Open</option>
            <option value="ALL">All</option>
          </select>
          <DateInput
            value={from}
            onChange={(e) => onFromChange(e.target.value)}
            placeholder="From"
            className="px-2 py-1"
          />
          <DateInput
            value={to}
            onChange={(e) => onToChange(e.target.value)}
            placeholder="To"
            className="px-2 py-1"
          />
        </div>
      </div>

      {isLoading ? (
        <Loading compact />
      ) : error ? (
        <ErrorFallback error={error} message="Could not load tickets by location." onRetry={onRetry} />
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">No tickets match the selected filters.</p>
      ) : (
        <div className="space-y-4">
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={rows} margin={{ top: 10, right: 20, left: 0, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="locationName" tick={{ fontSize: 12 }} />
                <YAxis allowDecimals={false} />
                <Tooltip />
                <Legend />
                <Bar dataKey="openIncidents" name="Incidents" stackId="a" fill={COLORS[0]} />
                <Bar dataKey="openServiceRequests" name="Service Requests" stackId="a" fill={COLORS[1]} />
              </BarChart>
            </ResponsiveContainer>
          </div>

          <DataTable<TicketsByLocationRow>
            caption="Tickets by location"
            columns={[
              { key: 'locationName', header: 'Location' },
              { key: 'totalOpen', header: 'Total Open' },
              { key: 'openIncidents', header: 'Incidents' },
              { key: 'openServiceRequests', header: 'Service Requests' },
              { key: 'oldestOpenDays', header: 'Oldest Open (days)', render: (row) => row.oldestOpenDays ?? '—' },
              { key: 'resolvedCount', header: 'Resolved' },
              { key: 'breachedCount', header: 'Breached' },
            ]}
            data={rows}
            getRowKey={(row) => row.locationId}
            emptyText="No tickets match the selected filters."
          />
        </div>
      )}
    </div>
  )
}

function TierQueue({ tier, instance, account }: {
  tier: { id: string; label: string }
  instance: ReturnType<typeof useMsal>['instance']
  account: ReturnType<typeof useMsal>['accounts'][number]
}) {
  const query = useQuery<IncidentSummary[]>({
    queryKey: ['dashboard', 'tier-queue', tier.id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, `/api/v1/incidents?status=${ACTIVE_STATUSES}&teamId=${tier.id}&limit=20`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    refetchInterval: 60_000,
    staleTime: 0,
  })

  return (
    <IncidentSection
      title={`Your Tier Queue — ${tier.label}`}
      caption={`Active incidents in the ${tier.label} queue`}
      data={sortByUrgency(query.data ?? [])}
      isLoading={query.isLoading}
      emptyText={`No active incidents in the ${tier.label} queue.`}
    />
  )
}
