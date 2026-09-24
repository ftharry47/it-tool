import { Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery } from '@tanstack/react-query'
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
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { Loading } from '../../components/ui/Loading'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { SlaCountdown } from '../../components/ui/SlaCountdown'
import { formatDate, formatDateTime } from '../../lib/date'

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
  openByType?: {
    incidents?: number
    serviceRequests?: number
    problems?: number
    changes?: number
  }
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
  assigneeId: string | null
  location: string | null
  phone: string | null
  createdAt: string
  slaBreachStatus: string | null
  responseDueAt: string | null
  resolutionDueAt: string | null
  responseMetAt: string | null
  resolutionMetAt: string | null
  hasBeenTierEscalated: boolean
}

interface ServiceRequestResponse {
  id: string
  number: string
  catalogItemName: string | null
  requesterName: string | null
  status: string
  createdAt: string
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
  agentId: string
  agentName: string
  openCount: number
  onTrack: number
  atRisk: number
  breached: number
  noSla: number
}

interface QueueTicket {
  id: string
  number: string
  title: string | null
  status: string
  createdAt: string | null
  slaStatus: string | null
}

interface NeedsAttentionData {
  unassignedIncidents: QueueTicket[]
  breachedSlaCount: number
  rejectedNeedsReview: QueueTicket[]
  escalationsAwaitingPickup: QueueTicket[]
  pendingApprovals: number
  kbPendingReview: number
  unassignedFulfillmentTasks: number
}

interface ConfigHealth {
  emptyTeams: string[]
  locationsWithoutApprover: string[]
  itemsNeedingApprover: string[]
  policiesWithoutTiers: string[]
}

interface ChangeCalendarData {
  changes: {
    id: string
    number: string
    title: string
    locationId: string | null
    locationName: string | null
    plannedStart: string
    plannedEnd: string
  }[]
  conflicts: { changeA: string; changeB: string }[]
}

interface ServiceRequestOps {
  byStatus: Record<string, number>
  oldestOpen: QueueTicket[]
  needsAttention: {
    pendingApprovals: number
    rejectedNeedsReview: number
    unassignedTasks: number
  }
  aging: { status: string; age0to1: number; age2to3: number; age4to7: number; age8plus: number }[]
  slaAtRisk: number
  overdueDeliveries?: {
    count: number
    oldest?: { taskId: string; requestId: string; requestNumber: string; description: string; expectedDeliveryDate: string }
  }
  approverBacklog: { approver: string; count: number; oldestDays: number }[]
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
  escalatedAwayPct: number
  score: number
  grade: string
  byType?: Record<string, { handled: number; resolved: number }>
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

/** Merged monthly view: performance stats + grade + per-type volume, all in one card. */
function MyMonthCard({ data, isLoading }: { data: MyPerformanceResponse | undefined; isLoading: boolean }) {
  if (isLoading) {
    return (
      <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
        <p className="text-sm text-muted-foreground">Loading your monthly stats…</p>
      </div>
    )
  }
  if (!data) return null
  const r = data.report
  const monthLabel = (() => {
    const [y, m] = r.period.split('-').map(Number)
    return y && m ? new Date(y, m - 1).toLocaleString('en-US', { month: 'long', year: 'numeric' }) : r.period
  })()
  const types: { label: string; stats: { handled: number; resolved: number } }[] = [
    { label: 'Incidents', stats: r.byType?.incidents ?? { handled: 0, resolved: 0 } },
    { label: 'Service Requests', stats: r.byType?.serviceRequests ?? { handled: 0, resolved: 0 } },
    { label: 'Problems', stats: r.byType?.problems ?? { handled: 0, resolved: 0 } },
    { label: 'Changes', stats: r.byType?.changes ?? { handled: 0, resolved: 0 } },
  ]
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-medium text-muted-foreground">This Month — Your Work</h3>
          <span className="text-xs text-muted-foreground">{monthLabel}</span>
        </div>
        <GradeChip grade={r.grade} />
      </div>
      <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
        <div>
          <p className="text-2xl font-bold">{r.ticketsHandled}</p>
          <p className="text-xs text-muted-foreground">Handled</p>
        </div>
        <div>
          <p className="text-2xl font-bold">{r.ticketsResolved}</p>
          <p className="text-xs text-muted-foreground">Resolved</p>
        </div>
        <div>
          <p className="text-2xl font-bold">{r.resolutionRate}%</p>
          <p className="text-xs text-muted-foreground">Resolution rate</p>
        </div>
        <div>
          <p className="text-2xl font-bold">{r.slaCompliancePct >= 0 ? `${r.slaCompliancePct}%` : '—'}</p>
          <p className="text-xs text-muted-foreground">SLA (month)</p>
        </div>
        <div>
          <p className="text-2xl font-bold">{data.rollingSlaCompliancePct >= 0 ? `${data.rollingSlaCompliancePct}%` : '—'}</p>
          <p className="text-xs text-muted-foreground">SLA (all-time)</p>
        </div>
        <div>
          <p className="text-2xl font-bold">{r.escalatedAwayPct}%</p>
          <p className="text-xs text-muted-foreground">Escalated away</p>
        </div>
        <div>
          <p className="text-2xl font-bold">{r.score}</p>
          <p className="text-xs text-muted-foreground">Score / 100</p>
        </div>
      </div>
      <ul className="mt-4 space-y-2 border-t border-border pt-3">
        {types.map(({ label, stats }) => {
          const pct = stats.handled > 0 ? Math.round((stats.resolved / stats.handled) * 100) : 0
          return (
            <li key={label} className="flex items-center gap-4 text-sm">
              <span className="w-32 text-muted-foreground">{label}</span>
              <div className="flex h-2 min-w-0 flex-1 overflow-hidden rounded-full bg-muted">
                {stats.handled > 0 && (
                  <div
                    className="bg-emerald-500"
                    title={`${stats.resolved} of ${stats.handled} resolved`}
                    style={{ width: `${pct}%` }}
                  />
                )}
              </div>
              <span className="w-28 text-right text-xs tabular-nums text-muted-foreground">
                {stats.handled} handled · {stats.resolved} resolved
              </span>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

interface SlaTarget {
  id: string
  name: string
  appliesTo: string | null
  priorityFilter: string | null
  workflowType: string | null
  responseTargetMinutes: number
  resolutionTargetMinutes: number
  calendar: { id: string; name: string; timezone: string; workingHours: string; holidays: string } | null
  escalationTiers: {
    level: number
    triggerType: string | null
    stuckStatus: string | null
    stuckMinutes: number | null
    notifyRole: string | null
    reassignToTeamId: string | null
  }[]
}

const APPLIES_TO_LABEL: Record<string, string> = {
  INCIDENT: 'Incidents',
  REQUEST: 'Requests',
  PROBLEM: 'Problems',
  CHANGE: 'Changes',
}

const TRIGGER_LABEL: Record<string, string> = {
  ON_RESPONSE_BREACH: 'response breach',
  ON_RESOLUTION_BREACH: 'resolution breach',
  ON_STUCK_STATUS: 'stuck status',
}

function fmtTargetMinutes(minutes: number): string {
  if (minutes % 60 === 0) return `${minutes / 60}h`
  return `${minutes}m`
}

/**
 * The SLA policies governing the signed-in agent's work, grouped by what they
 * actually do: fulfillment-only agents (on IT Fulfillment but no L-tier) see
 * only Service Request SLAs; tier agents who also fulfill see both groups.
 */
function YourSlaTargets({ data, isLoading, fulfillmentOnly }: { data: SlaTarget[] | undefined; isLoading: boolean; fulfillmentOnly: boolean }) {
  if (isLoading || !data || data.length === 0) return null
  const requestPolicies = data.filter((p) => p.appliesTo === 'REQUEST')
  const ticketPolicies = fulfillmentOnly ? [] : data.filter((p) => p.appliesTo !== 'REQUEST')
  if (requestPolicies.length === 0 && ticketPolicies.length === 0) return null

  const renderPolicy = (p: SlaTarget) => (
          <div key={p.id} className="rounded-lg border border-border p-3">
            <div className="flex items-center justify-between gap-2">
              <p className="text-sm font-medium">{p.name}</p>
              <span className="rounded bg-muted px-1.5 py-0.5 text-xs font-medium text-muted-foreground">
                {p.appliesTo ? (APPLIES_TO_LABEL[p.appliesTo] ?? p.appliesTo) : '—'}
                {p.priorityFilter ? ` · ${p.priorityFilter}` : ''}
                {p.workflowType ? ` · ${p.workflowType}` : ''}
              </span>
            </div>
            <p className="mt-1 text-sm">
              Response <span className="font-medium">{fmtTargetMinutes(p.responseTargetMinutes)}</span>
              <span className="text-muted-foreground"> · </span>
              Resolution <span className="font-medium">{fmtTargetMinutes(p.resolutionTargetMinutes)}</span>
            </p>
            {p.calendar && (
              <p className="mt-1 text-xs text-muted-foreground">Business hours: {p.calendar.name} ({p.calendar.timezone})</p>
            )}
            {p.escalationTiers.length > 0 && (
              <p className="mt-1 text-xs text-muted-foreground">
                Escalates on {p.escalationTiers.map((t) => TRIGGER_LABEL[t.triggerType ?? ''] ?? t.triggerType).join(', ')}
              </p>
            )}
          </div>
  )

  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <h3 className="mb-3 text-sm font-medium text-muted-foreground">Your SLA Targets</h3>
      {ticketPolicies.length > 0 && (
        <div className={requestPolicies.length > 0 ? 'mb-4' : ''}>
          {requestPolicies.length > 0 && (
            <p className="mb-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">Incidents · Problems · Changes</p>
          )}
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            {ticketPolicies.map(renderPolicy)}
          </div>
        </div>
      )}
      {requestPolicies.length > 0 && (
        <div>
          {ticketPolicies.length > 0 && (
            <p className="mb-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">Service Requests</p>
          )}
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            {requestPolicies.map(renderPolicy)}
          </div>
        </div>
      )}
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
      <div className="overflow-x-auto scrollbar-themed">
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
              <th className="pb-2 pr-4 font-medium">Esc. %</th>
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
                <td className="py-2 pr-4">{r.escalatedAwayPct}%</td>
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

function MetricCard({ label, value, to }: { label: string; value: number | string; to?: string }) {
  const inner = (
    <div className="h-full rounded-xl border border-border bg-card p-4 text-center shadow-sm transition-shadow duration-150 hover:shadow-md">
      <p className="text-2xl font-bold">{value}</p>
      <p className="text-xs text-muted-foreground">{label}</p>
    </div>
  )
  return to ? <Link to={to} className="block">{inner}</Link> : inner
}

const ACTIVE_STATUSES = 'NEW,IN_PROGRESS,ON_HOLD,REOPENED'

function SlaView({ data, trend }: { data: SlaCompliance; trend?: MonthlySla[] }) {
  const delta = trend != null && trend.length >= 2
    ? Math.round((trend[trend.length - 1].compliancePercent - trend[trend.length - 2].compliancePercent) * 10) / 10
    : null
  if (data.total === 0) {
    return (
      <div className="space-y-4 rounded-xl border border-border bg-card p-4 shadow-sm">
        <h3 className="text-sm font-medium text-muted-foreground">SLA Health</h3>
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
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-muted-foreground">SLA Health</h3>
        <Link to="/dashboard/sla" className="text-xs text-muted-foreground hover:text-foreground hover:underline">
          SLA details →
        </Link>
      </div>
      <div className="grid grid-cols-3 gap-4">
        <MetricCard label="Compliance" value={`${data.compliancePercent}%`} />
        <MetricCard label="Total" value={data.total} />
        <MetricCard label="Breached" value={data.breached} to="/dashboard/sla?breachStatus=BREACHED" />
      </div>
      {delta != null && (
        <p className={`text-xs font-medium ${delta >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
          {delta >= 0 ? '▲' : '▼'} {Math.abs(delta)}% vs {trend![trend!.length - 2].month} — {trend![trend!.length - 1].breached} of {trend![trend!.length - 1].total} breached this month
        </p>
      )}
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

const OPEN_BY_TYPE_LINKS: { key: keyof NonNullable<TicketsSummary['openByType']>; label: string; to: string }[] = [
  { key: 'incidents', label: 'Incidents', to: '/dashboard/incidents?status=NEW,IN_PROGRESS,ON_HOLD,REOPENED' },
  { key: 'serviceRequests', label: 'Requests', to: '/dashboard/service-requests' },
  { key: 'problems', label: 'Problems', to: '/dashboard/problems' },
  { key: 'changes', label: 'Changes', to: '/dashboard/changes' },
]

/** Merged ticket health: queue metrics + status pie + open work by type + priority mix. */
function TicketsView({ data, priority }: { data: TicketsSummary; priority: AdHocQueryResponse | undefined }) {
  const closed = Math.max(0, data.total - data.open - data.inProgress - data.resolvedToday)
  const chartData = [
    { name: 'Open', value: data.open },
    { name: 'In Progress', value: data.inProgress },
    { name: 'Resolved Today', value: data.resolvedToday },
    { name: 'Closed', value: closed },
  ].filter((d) => d.value > 0)
  const priorityData = (priority?.rows ?? [])
    .map((row) => ({ name: row.group ?? 'Unassigned', count: row.count }))
    .filter((d) => d.count > 0)
  return (
    <div className="space-y-4 rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-muted-foreground">Ticket Pipeline</h3>
        <Link to="/dashboard/incidents" className="text-xs text-muted-foreground hover:text-foreground hover:underline">
          View all incidents →
        </Link>
      </div>
      {data.openByType && (
        <div className="flex flex-wrap gap-2">
          {OPEN_BY_TYPE_LINKS.map((t) => (
            <Link
              key={t.key}
              to={t.to}
              className="rounded-md bg-muted px-2 py-1 text-xs font-medium text-foreground transition hover:opacity-80"
            >
              {t.label}: {data.openByType?.[t.key] ?? 0} open
            </Link>
          ))}
        </div>
      )}
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <MetricCard label="Total" value={data.total} />
        <MetricCard label="Open" value={data.open} />
        <MetricCard label="In Progress" value={data.inProgress} />
        <MetricCard label="Resolved Today" value={data.resolvedToday} />
      </div>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <p className="mb-1 text-xs text-muted-foreground">Open incidents by status</p>
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
        {priorityData.length > 0 && (
          <div>
            <p className="mb-1 text-xs text-muted-foreground">Open incidents by priority</p>
            <div className="h-56">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={priorityData} dataKey="count" nameKey="name" outerRadius={70} label>
                    {priorityData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                  </Pie>
                  <Tooltip />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </div>
        )}
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

const WORKLOAD_SEGMENTS: { key: keyof AgentWorkload; label: string; color: string }[] = [
  { key: 'onTrack', label: 'On track', color: SLA_SEGMENT_COLORS.onTrack },
  { key: 'atRisk', label: 'At risk', color: SLA_SEGMENT_COLORS.atRisk },
  { key: 'breached', label: 'Breached', color: SLA_SEGMENT_COLORS.breached },
  { key: 'noSla', label: 'No SLA', color: SLA_SEGMENT_COLORS.noSla },
]

function WorkloadChart({ data, isLoading }: { data: AgentWorkload[] | undefined; isLoading: boolean }) {
  const rows = (data ?? []).filter((r) => r.openCount > 0)
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <h3 className="mb-2 text-sm font-medium text-muted-foreground">Workload per Agent</h3>
      {isLoading ? (
        <Loading compact />
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">No open assigned tickets.</p>
      ) : rows.length <= 15 ? (
        <>
          <ul className="divide-y divide-border">
            {rows.map((r) => {
              const onTrackPct = Math.round((r.onTrack * 100) / r.openCount)
              return (
                <li key={r.agentName} className="flex items-center gap-4 py-2.5">
                  <span className="w-36 truncate text-sm font-medium" title={r.agentName}>{r.agentName}</span>
                  <div className="flex h-2.5 min-w-0 flex-1 overflow-hidden rounded-full bg-muted">
                    {WORKLOAD_SEGMENTS.filter((s) => (r[s.key] as number) > 0).map((s) => (
                      <div
                        key={s.key}
                        title={`${s.label}: ${r[s.key]}`}
                        style={{ width: `${((r[s.key] as number) / r.openCount) * 100}%`, backgroundColor: s.color }}
                      />
                    ))}
                  </div>
                  <Link
                    to={`/dashboard/agent-queue?agentId=${r.agentId}`}
                    className="w-14 text-right text-sm tabular-nums text-muted-foreground underline-offset-2 hover:text-foreground hover:underline"
                    title={`Open ${r.agentName}'s queue`}
                  >
                    {r.openCount} open
                  </Link>
                  <span
                    className={`w-11 text-right text-xs font-semibold ${
                      onTrackPct >= 80
                        ? 'text-emerald-600 dark:text-emerald-400'
                        : onTrackPct >= 50
                          ? 'text-amber-600 dark:text-amber-400'
                          : 'text-red-600 dark:text-red-400'
                    }`}
                    title="Share of open tickets on track against SLA"
                  >
                    {onTrackPct}%
                  </span>
                </li>
              )
            })}
          </ul>
          <div className="mt-2 flex flex-wrap gap-3">
            {WORKLOAD_SEGMENTS.map((s) => (
              <span key={s.key} className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
                <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: s.color }} />
                {s.label}
              </span>
            ))}
          </div>
        </>
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

type RecentlyWorkedItem = {
  key: string
  kind: 'Incident' | 'Request'
  id: string
  number: string
  title: string
  status: string
  assignee: string | null
  createdAt: string
  escalated: boolean
}

function RecentlyWorked({ incidents, requests, isLoading }: {
  incidents: IncidentSummary[] | undefined
  requests: ServiceRequestResponse[] | undefined
  isLoading: boolean
}) {
  if (isLoading) return <Loading compact />
  const items: RecentlyWorkedItem[] = [
    ...(incidents ?? []).map((i) => ({
      key: `i-${i.id}`,
      kind: 'Incident' as const,
      id: i.id,
      number: `INC-${i.number}`,
      title: i.title,
      status: i.status,
      assignee: i.assignee,
      createdAt: i.createdAt,
      escalated: i.hasBeenTierEscalated,
    })),
    ...(requests ?? []).map((r) => ({
      key: `r-${r.id}`,
      kind: 'Request' as const,
      id: r.id,
      number: r.number,
      title: r.catalogItemName ?? 'Service request',
      status: r.status,
      assignee: r.requesterName,
      createdAt: r.createdAt,
      escalated: false,
    })),
  ].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 10)

  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <h3 className="mb-3 text-sm font-medium text-muted-foreground">Recently Worked</h3>
      {items.length === 0 ? (
        <p className="text-sm text-muted-foreground">No previously assigned incidents or service requests.</p>
      ) : (
        <ul className="space-y-2">
          {items.map((item) => (
            <li key={item.key} className="flex items-center justify-between gap-3 text-sm">
              <div className="min-w-0">
                <span className="mr-2 rounded bg-muted px-1.5 py-0.5 text-xs font-medium text-muted-foreground">
                  {item.kind}
                </span>
                <Link
                  to={item.kind === 'Incident' ? `/dashboard/incidents/${item.id}` : `/dashboard/service-requests/${item.id}`}
                  className="font-medium hover:underline"
                >
                  {item.number}
                </Link>
                <span className="text-muted-foreground"> · {item.title}</span>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                {item.escalated && (
                  <span className="rounded bg-amber-500/10 px-1.5 py-0.5 text-xs font-medium text-amber-700">
                    Escalated
                  </span>
                )}
                <StatusBadge status={item.status} />
                <span className="text-xs text-muted-foreground">{formatDate(item.createdAt)}</span>
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

  const isAdmin = !!currentUser?.roles.some((r) => ['ADMIN', 'SUPER_ADMIN'].includes(r))
  const isSuperAdmin = !!currentUser?.roles.includes('SUPER_ADMIN')
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
    refetchInterval: 300_000,
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
    refetchInterval: 300_000,
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
    refetchInterval: 300_000,
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
    refetchInterval: 300_000,
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
    refetchInterval: 300_000,
    staleTime: 0,
  })

  const needsAttentionQuery = useQuery<NeedsAttentionData>({
    queryKey: ['dashboard', 'needs-attention'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/reports/needs-attention')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && isAdmin,
    refetchInterval: 30_000,
    staleTime: 0,
  })

  const srOpsQuery = useQuery<ServiceRequestOps>({
    queryKey: ['dashboard', 'sr-ops'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/reports/service-request-ops')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && isAdmin,
    refetchInterval: 30_000,
    staleTime: 0,
  })

  // SUPER_ADMIN config audit — empty tier teams, unresolvable approvals,
  // tierless SLA policies.
  const configHealthQuery = useQuery<ConfigHealth>({
    queryKey: ['dashboard', 'config-health'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/admin/config-health')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && isSuperAdmin,
    refetchInterval: 300_000,
    staleTime: 0,
  })

  // This week's scheduled changes + same-location overlap conflicts.
  const changesWeekQuery = useQuery<ChangeCalendarData>({
    queryKey: ['dashboard', 'changes-this-week'],
    queryFn: async () => {
      const from = new Date()
      const to = new Date(Date.now() + 7 * 86_400_000)
      const res = await fetchWithToken(instance, account,
          `/api/v1/changes/calendar?from=${from.toISOString()}&to=${to.toISOString()}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && isAdmin,
    refetchInterval: 300_000,
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
    refetchInterval: 300_000,
    staleTime: 0,
  })

  const myProblemsQuery = useQuery<ProblemSummary[]>({
    queryKey: ['dashboard', 'my-problems', currentUser?.id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/problems?mine=true')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!currentUser && isStaff,
    refetchInterval: 300_000,
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
    refetchInterval: 300_000,
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
    refetchInterval: 300_000,
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
    refetchInterval: 300_000,
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
    enabled: !!account && isAdmin,
    refetchInterval: 300_000,
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
    refetchInterval: 300_000,
    staleTime: 0,
  })

  const recentIncidentsQuery = useQuery<IncidentSummary[]>({
    queryKey: ['dashboard', 'recently-worked-incidents', currentUser?.id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/incidents/recently-worked')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!currentUser && isStaff,
    refetchInterval: 300_000,
    staleTime: 0,
  })

  const recentRequestsQuery = useQuery<ServiceRequestResponse[]>({
    queryKey: ['dashboard', 'recently-worked-requests', currentUser?.id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/service-requests/recently-worked')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!currentUser && isStaff,
    refetchInterval: 300_000,
    staleTime: 0,
  })

  // Part B2: SLA policies governing the caller's own work.
  const mySlaTargetsQuery = useQuery<SlaTarget[]>({
    queryKey: ['dashboard', 'my-sla-targets', currentUser?.id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/reports/my-sla-targets')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!currentUser && isStaff,
    refetchInterval: 300_000,
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
                {/* Row 1 — KPI strip: the four triage numbers, each a drill link. */}
                <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                  <MetricCard label="Open tickets" value={ticketsQuery.data?.open ?? 0} to="/dashboard/incidents?status=NEW,IN_PROGRESS,ON_HOLD,REOPENED" />
                  <MetricCard label="Breached SLAs" value={slaQuery.data?.breached ?? 0} to="/dashboard/sla?breachStatus=BREACHED" />
                  <MetricCard label="Pending approvals" value={needsAttentionQuery.data?.pendingApprovals ?? 0} to="/dashboard/service-requests?status=PENDING_APPROVAL" />
                  <MetricCard label="Unassigned" value={ticketsQuery.data?.unassigned ?? 0} to="/dashboard/incidents?assignee=__unassigned__" />
                </div>

                {/* Row 2 — triage + config audit. */}
                <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                  <NeedsAttentionCard data={needsAttentionQuery.data} isLoading={needsAttentionQuery.isLoading} />
                  {isSuperAdmin
                    ? <ConfigHealthCard data={configHealthQuery.data} isLoading={configHealthQuery.isLoading} />
                    : <div />}
                </div>

                {/* Row 3 — ticket + SLA health. */}
                <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                  {ticketsQuery.data && <TicketsView data={ticketsQuery.data} priority={priorityQuery.data} />}
                  {slaQuery.data && <SlaView data={slaQuery.data} trend={slaTrendQuery.data} />}
                </div>

                {/* Row 4 — people: current workload snapshot + escalation history. */}
                <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                  <WorkloadChart data={workloadQuery.data} isLoading={workloadQuery.isLoading} />
                  <EscalationsFeed data={escalationsQuery.data} isLoading={escalationsQuery.isLoading} />
                </div>

                {/* Row 5 — requests & changes operations. */}
                <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                  <ServiceRequestOpsCard data={srOpsQuery.data} isLoading={srOpsQuery.isLoading} />
                  <ChangesThisWeekCard data={changesWeekQuery.data} isLoading={changesWeekQuery.isLoading} />
                </div>

                {/* Row 6 — SUPER_ADMIN agent performance. */}
                {isSuperAdmin && (
                  <AgentPerformanceTable reports={parsePerfReports(allPerfQuery.data)} isLoading={allPerfQuery.isLoading} />
                )}
              </>
            )}

            <MyMonthCard data={myPerfQuery.data} isLoading={myPerfQuery.isLoading} />

            <YourSlaTargets
              data={mySlaTargetsQuery.data}
              isLoading={mySlaTargetsQuery.isLoading}
              fulfillmentOnly={isFulfillmentMember && myTierTeams.length === 0}
            />

            <IncidentSection
              title="My Tickets"
              caption="Incidents assigned to you, sorted by urgency"
              data={sortByUrgency(myTicketsQuery.data ?? [])}
              isLoading={myTicketsQuery.isLoading}
              emptyText="No incidents assigned to you."
            />

            {isStaff && (
              <RecentlyWorked
                incidents={recentIncidentsQuery.data}
                requests={recentRequestsQuery.data}
                isLoading={recentIncidentsQuery.isLoading || recentRequestsQuery.isLoading}
              />
            )}

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

const QUEUE_LINKS: Record<string, string> = {
  INCIDENT: '/dashboard/incidents',
  SERVICE_REQUEST: '/dashboard/service-requests',
  PROBLEM: '/dashboard/problems',
  CHANGE: '/dashboard/changes',
}

function queueTicketLink(type: string, id: string) {
  return `${QUEUE_LINKS[type] ?? '/dashboard'}/${id}`
}

function ageDays(createdAt: string | null): number | null {
  if (!createdAt) return null
  return Math.max(0, Math.floor((Date.now() - new Date(createdAt).getTime()) / 86_400_000))
}

/** SUPER_ADMIN — silently-broken configuration that would fail at runtime. */
function ConfigHealthCard({ data, isLoading }: { data: ConfigHealth | undefined; isLoading: boolean }) {
  if (isLoading) {
    return (
      <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
        <Loading compact />
      </div>
    )
  }
  if (!data) return null

  const groups: { label: string; items: string[]; to: string }[] = [
    { label: 'Support/fulfillment teams with no members', items: data.emptyTeams, to: '/admin/support-tiers' },
    { label: 'Locations with no approval manager', items: data.locationsWithoutApprover, to: '/admin/locations' },
    { label: 'Approval-gated catalog items with no fallback approver', items: data.itemsNeedingApprover, to: '/admin/catalog' },
    { label: 'SLA policies with no escalation tiers', items: data.policiesWithoutTiers, to: '/dashboard/sla' },
  ]
  const total = groups.reduce((n, g) => n + g.items.length, 0)

  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-muted-foreground">Configuration Health</h3>
        {total > 0 && (
          <span className="rounded bg-amber-100 px-1.5 py-0.5 text-xs font-semibold text-amber-700 dark:bg-amber-950 dark:text-amber-300">
            {total} issue{total === 1 ? '' : 's'}
          </span>
        )}
      </div>
      {total === 0 ? (
        <p className="mt-2 text-sm text-emerald-600 dark:text-emerald-400">
          All clear — teams staffed, approval routing resolvable, SLA tiers configured.
        </p>
      ) : (
        <ul className="mt-3 space-y-2">
          {groups.filter((g) => g.items.length > 0).map((g) => (
            <li key={g.label}>
              <Link to={g.to} className="text-xs font-semibold text-amber-600 hover:underline dark:text-amber-400">
                {g.label} ({g.items.length}) →
              </Link>
              <p className="mt-0.5 truncate text-xs text-muted-foreground">{g.items.join(', ')}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/** This week's scheduled changes + location/time conflicts from the calendar. */
function ChangesThisWeekCard({ data, isLoading }: { data: ChangeCalendarData | undefined; isLoading: boolean }) {
  if (isLoading) {
    return (
      <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
        <Loading compact />
      </div>
    )
  }
  if (!data) return null

  const conflictIds = new Set(data.conflicts.flatMap((c) => [c.changeA, c.changeB]))
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-muted-foreground">Changes This Week</h3>
        <Link to="/dashboard/changes/calendar" className="text-xs text-muted-foreground hover:text-foreground hover:underline">
          Calendar →
        </Link>
      </div>
      <div className="mt-3 flex flex-wrap gap-2">
        <Link to="/dashboard/changes/calendar" className="rounded-md bg-muted px-2 py-1 text-xs font-medium text-foreground transition hover:opacity-80">
          {data.changes.length} scheduled
        </Link>
        {data.conflicts.length > 0 && (
          <span className="rounded-md bg-red-100 px-2 py-1 text-xs font-medium text-red-700 dark:bg-red-950 dark:text-red-300">
            {data.conflicts.length} conflict{data.conflicts.length === 1 ? '' : 's'} — same location, overlapping window
          </span>
        )}
      </div>
      {data.changes.length === 0 ? (
        <p className="mt-3 text-sm text-muted-foreground">No changes scheduled this week.</p>
      ) : (
        <ul className="mt-3 divide-y divide-border">
          {data.changes.slice(0, 6).map((c) => (
            <li key={c.id} className="flex items-center gap-3 py-1.5 text-sm">
              <Link to={`/dashboard/changes/${c.id}`} className="w-24 shrink-0 font-medium hover:underline">
                {c.number}
              </Link>
              <span className="min-w-0 flex-1 truncate text-muted-foreground">{c.title}</span>
              {conflictIds.has(c.id) && (
                <span className="rounded bg-red-100 px-1.5 py-0.5 text-[10px] font-semibold text-red-700 dark:bg-red-950 dark:text-red-300">
                  CONFLICT
                </span>
              )}
              <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
                {formatDate(c.plannedStart)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/** Part K: admin triage — what needs attention right now across all ticket types. */
function NeedsAttentionCard({ data, isLoading }: { data: NeedsAttentionData | undefined; isLoading: boolean }) {
  if (isLoading) {
    return (
      <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
        <Loading compact />
      </div>
    )
  }
  if (!data) return null

  const groups: { label: string; count: number; items: QueueTicket[]; type: string; tone: 'red' | 'amber' }[] = [
    {
      label: 'Unassigned incidents',
      count: data.unassignedIncidents.length,
      items: data.unassignedIncidents,
      type: 'INCIDENT',
      tone: 'red',
    },
    {
      label: 'Escalations awaiting pickup',
      count: data.escalationsAwaitingPickup.length,
      items: data.escalationsAwaitingPickup,
      type: 'INCIDENT',
      tone: 'amber',
    },
    {
      label: 'Rejected requests awaiting review',
      count: data.rejectedNeedsReview.length,
      items: data.rejectedNeedsReview,
      type: 'SERVICE_REQUEST',
      tone: 'amber',
    },
  ]
  const totalFlags = groups.reduce((n, g) => n + g.count, 0) + data.breachedSlaCount
      + data.pendingApprovals + (data.kbPendingReview ?? 0) + (data.unassignedFulfillmentTasks ?? 0)
  if (totalFlags === 0) {
    return (
      <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
        <h3 className="text-sm font-medium text-muted-foreground">Needs Attention</h3>
        <p className="mt-2 text-sm text-emerald-600 dark:text-emerald-400">All clear — nothing waiting on you.</p>
      </div>
    )
  }

  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-muted-foreground">Needs Attention</h3>
        <span className="rounded bg-red-100 px-1.5 py-0.5 text-xs font-semibold text-red-700 dark:bg-red-950 dark:text-red-300">
          {totalFlags} open item{totalFlags === 1 ? '' : 's'}
        </span>
      </div>
      <div className="mt-3 flex flex-wrap gap-2 text-xs">
        {data.breachedSlaCount > 0 && (
          <Link to="/dashboard/sla" className="rounded-md bg-red-100 px-2 py-1 font-medium text-red-700 hover:bg-red-200 dark:bg-red-950 dark:text-red-300 dark:hover:bg-red-900">
            {data.breachedSlaCount} breached SLA{data.breachedSlaCount === 1 ? '' : 's'}
          </Link>
        )}
        {data.pendingApprovals > 0 && (
          <Link to="/dashboard/service-requests?status=PENDING_APPROVAL" className="rounded-md bg-amber-100 px-2 py-1 font-medium text-amber-700 hover:bg-amber-200 dark:bg-amber-950 dark:text-amber-300 dark:hover:bg-amber-900">
            {data.pendingApprovals} pending approval{data.pendingApprovals === 1 ? '' : 's'}
          </Link>
        )}
        {(data.unassignedFulfillmentTasks ?? 0) > 0 && (
          <Link to="/dashboard/service-requests?status=IN_FULFILLMENT" className="rounded-md bg-amber-100 px-2 py-1 font-medium text-amber-700 hover:bg-amber-200 dark:bg-amber-950 dark:text-amber-300 dark:hover:bg-amber-900">
            {data.unassignedFulfillmentTasks} fulfillment task{data.unassignedFulfillmentTasks === 1 ? '' : 's'} unassigned
          </Link>
        )}
        {(data.kbPendingReview ?? 0) > 0 && (
          <Link to="/dashboard/kb" className="rounded-md bg-muted px-2 py-1 font-medium text-foreground transition hover:opacity-80">
            {data.kbPendingReview} KB article{data.kbPendingReview === 1 ? '' : 's'} pending review
          </Link>
        )}
      </div>
      <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-3">
        {groups.filter((g) => g.count > 0).map((g) => (
          <div key={g.label}>
            <p className={`text-xs font-semibold ${g.tone === 'red' ? 'text-red-600 dark:text-red-400' : 'text-amber-600 dark:text-amber-400'}`}>
              {g.label} ({g.count})
            </p>
            <ul className="mt-1 space-y-1">
              {g.items.slice(0, 4).map((t) => {
                const days = ageDays(t.createdAt)
                return (
                  <li key={t.id} className="text-sm">
                    <Link to={queueTicketLink(g.type, t.id)} className="font-medium hover:underline">
                      {t.number}
                    </Link>
                    <span className="ml-1.5 text-muted-foreground">
                      {t.title ? t.title.slice(0, 32) : ''}
                      {days != null && days > 0 ? ` · ${days}d old` : ''}
                    </span>
                  </li>
                )
              })}
            </ul>
          </div>
        ))}
      </div>
    </div>
  )
}

const SR_STATUS_LABEL: Record<string, string> = {
  SUBMITTED: 'Submitted',
  PENDING_APPROVAL: 'Pending Approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  REJECTED_NEEDS_REVIEW: 'Needs Review',
  IN_FULFILLMENT: 'In Fulfillment',
  ON_HOLD: 'On Hold',
  FULFILLED: 'Fulfilled',
  CANCELLED: 'Cancelled',
}

/** Part G: live service-request operations board for admins. */
function ServiceRequestOpsCard({ data, isLoading }: { data: ServiceRequestOps | undefined; isLoading: boolean }) {
  if (isLoading) {
    return (
      <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
        <Loading compact />
      </div>
    )
  }
  if (!data) return null

  const OPEN_STATUSES = ['SUBMITTED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED_NEEDS_REVIEW', 'IN_FULFILLMENT', 'ON_HOLD']
  const openTotal = OPEN_STATUSES.reduce((n, s) => n + (data.byStatus[s] ?? 0), 0)

  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-muted-foreground">Service Request Operations</h3>
        <Link to="/dashboard/service-requests" className="text-xs text-muted-foreground hover:text-foreground hover:underline">
          View all →
        </Link>
      </div>

      <div className="mt-3 flex flex-wrap gap-2">
        {OPEN_STATUSES.map((s) => {
          const n = data.byStatus[s] ?? 0
          const hot = s === 'PENDING_APPROVAL' || s === 'REJECTED_NEEDS_REVIEW'
          return (
            <Link
              key={s}
              to={`/dashboard/service-requests?status=${s}`}
              className={`rounded-md px-2 py-1 text-xs font-medium transition hover:opacity-80 ${
                n === 0
                  ? 'bg-muted text-muted-foreground'
                  : s === 'ON_HOLD'
                    ? 'bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300'
                    : hot
                      ? 'bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300'
                      : 'bg-muted text-foreground'
              }`}
            >
              {SR_STATUS_LABEL[s]}: {n}
            </Link>
          )
        })}
        <span className="rounded-md bg-muted px-2 py-1 text-xs font-medium text-muted-foreground">
          Open total: {openTotal}
        </span>
        {(data.slaAtRisk ?? 0) > 0 && (
          <span className="rounded-md bg-red-100 px-2 py-1 text-xs font-medium text-red-700 dark:bg-red-950 dark:text-red-300">
            SLA at risk/breached: {data.slaAtRisk}
          </span>
        )}
        {(data.overdueDeliveries?.count ?? 0) > 0 && (
          <Link
            to={data.overdueDeliveries?.oldest
              ? `/dashboard/service-requests/${data.overdueDeliveries.oldest.requestId}`
              : '/dashboard/service-requests?status=IN_FULFILLMENT'}
            className="rounded-md bg-red-100 px-2 py-1 text-xs font-medium text-red-700 hover:bg-red-200 dark:bg-red-950 dark:text-red-300 dark:hover:bg-red-900"
            title={data.overdueDeliveries?.oldest
              ? `Oldest: ${data.overdueDeliveries.oldest.requestNumber} — ${data.overdueDeliveries.oldest.description} (due ${data.overdueDeliveries.oldest.expectedDeliveryDate})`
              : undefined}
          >
            Overdue deliveries: {data.overdueDeliveries!.count}
          </Link>
        )}
      </div>

      {(data.approverBacklog ?? []).length > 0 && (
        <div className="mt-3">
          <p className="text-xs font-medium text-muted-foreground">Pending approvals by approver</p>
          <ul className="mt-1 space-y-1">
            {data.approverBacklog.map((a) => (
              <li key={a.approver} className="flex items-center justify-between text-xs">
                <span className="truncate">{a.approver}</span>
                <span className="tabular-nums text-muted-foreground">
                  {a.count} pending{a.oldestDays > 0 ? ` · oldest ${a.oldestDays}d` : ''}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {(data.aging ?? []).length > 0 && (
        <div className="mt-3">
          <p className="text-xs font-medium text-muted-foreground">Open-request aging (days since submitted)</p>
          <table className="mt-1 w-full text-xs">
            <thead>
              <tr className="text-left text-muted-foreground">
                <th className="py-1 font-medium">Status</th>
                <th className="py-1 text-right font-medium">0–1d</th>
                <th className="py-1 text-right font-medium">2–3d</th>
                <th className="py-1 text-right font-medium">4–7d</th>
                <th className="py-1 text-right font-medium">8d+</th>
              </tr>
            </thead>
            <tbody>
              {data.aging.map((row) => (
                <tr key={row.status} className="border-t border-border">
                  <td className="py-1">
                    <Link to={`/dashboard/service-requests?status=${row.status}`} className="hover:underline">
                      {SR_STATUS_LABEL[row.status] ?? row.status}
                    </Link>
                  </td>
                  <td className="py-1 text-right tabular-nums">{row.age0to1 || ''}</td>
                  <td className="py-1 text-right tabular-nums">{row.age2to3 || ''}</td>
                  <td className="py-1 text-right tabular-nums">{row.age4to7 || ''}</td>
                  <td className={`py-1 text-right tabular-nums ${row.age8plus ? 'font-semibold text-red-600 dark:text-red-400' : ''}`}>
                    {row.age8plus || ''}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data.oldestOpen.length > 0 && (
        <div className="mt-3">
          <p className="text-xs font-medium text-muted-foreground">Oldest open</p>
          <ul className="mt-1 divide-y divide-border">
            {data.oldestOpen.slice(0, 5).map((t) => {
              const days = ageDays(t.createdAt)
              return (
                <li key={t.id} className="flex items-center gap-3 py-1.5 text-sm">
                  <Link to={`/dashboard/service-requests/${t.id}`} className="w-24 shrink-0 font-medium hover:underline">
                    {t.number}
                  </Link>
                  <span className="min-w-0 flex-1 truncate text-muted-foreground">{t.title ?? ''}</span>
                  <StatusBadge status={t.status} />
                  {days != null && (
                    <span className={`w-16 text-right text-xs tabular-nums ${days >= 7 ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-foreground'}`}>
                      {days}d old
                    </span>
                  )}
                </li>
              )
            })}
          </ul>
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
    refetchInterval: 300_000,
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
