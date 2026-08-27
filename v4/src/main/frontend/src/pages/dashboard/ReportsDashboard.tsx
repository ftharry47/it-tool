import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { FileQuestion, FolderOpen } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
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
  agentId: string
  openCount: number
}

interface SprintVelocity {
  sprintId: string
  committed: number
  completed: number
}

function TicketsSummaryView({ data }: { data: TicketsSummary }) {
  const closed = Math.max(0, data.total - data.open - data.inProgress - data.resolvedToday)
  const chartData = [
    { name: 'Open', value: data.open },
    { name: 'In Progress', value: data.inProgress },
    { name: 'Resolved Today', value: data.resolvedToday },
    { name: 'Closed', value: closed },
  ].filter((d) => d.value > 0)

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <MetricCard label="Total" value={data.total} />
        <MetricCard label="Open" value={data.open} />
        <MetricCard label="In Progress" value={data.inProgress} />
        <MetricCard label="Resolved Today" value={data.resolvedToday} />
      </div>
      <div className="h-72 rounded-xl border border-border bg-card p-4 shadow-sm">
        <h3 className="mb-2 text-sm font-medium text-muted-foreground">Ticket distribution</h3>
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

function SlaComplianceView({ data }: { data: SlaCompliance }) {
  const nonBreached = Math.max(0, data.total - data.breached)
  const chartData = [
    { name: 'Compliant', value: nonBreached },
    { name: 'Breached', value: data.breached },
  ].filter((d) => d.value > 0)

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <MetricCard label="SLA Compliance" value={`${data.compliancePercent}%`} />
        <MetricCard label="Total SLAs" value={data.total} />
        <MetricCard label="Breached" value={data.breached} />
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

function AgentWorkloadView({ data }: { data: AgentWorkload[] }) {
  return (
    <div className="h-96 rounded-xl border border-border bg-card p-4 shadow-sm">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 20, right: 30, left: 0, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="agentId" tick={{ fontSize: 12 }} />
          <YAxis />
          <Tooltip />
          <Bar dataKey="openCount" name="Open Incidents" fill={COLORS[0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

function SprintVelocityView({ data }: { data: SprintVelocity[] }) {
  return (
    <div className="h-96 rounded-xl border border-border bg-card p-4 shadow-sm">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 20, right: 30, left: 0, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="sprintId" tick={{ fontSize: 12 }} />
          <YAxis />
          <Tooltip />
          <Legend />
          <Bar dataKey="committed" fill={COLORS[0]} />
          <Bar dataKey="completed" fill={COLORS[2]} />
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
  const [active, setActive] = useState('tickets')

  const activeTab = TABS.find((t) => t.key === active) ?? TABS[0]

  const query = useQuery<unknown>({
    queryKey: ['report', activeTab.key],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, activeTab.endpoint)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-2xl font-semibold tracking-tight">Reporting Dashboards</h1>
          <div className="flex gap-2">
            <Link to="/dashboard/reports/saved" className="inline-flex items-center gap-2 rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-muted">
              <FolderOpen className="h-4 w-4" />
              Saved Reports
            </Link>
            <Link to="/dashboard/reports/query" className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90">
              <FileQuestion className="h-4 w-4" />
              Query Builder
            </Link>
          </div>
        </div>

        <div className="flex flex-wrap gap-2 border-b border-border pb-2">
          {TABS.map((t) => (
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
            {active === 'tickets' && <TicketsSummaryView data={query.data as TicketsSummary} />}
            {active === 'sla' && <SlaComplianceView data={query.data as SlaCompliance} />}
            {active === 'agent' && <AgentWorkloadView data={query.data as AgentWorkload[]} />}
            {active === 'sprint' && <SprintVelocityView data={query.data as SprintVelocity[]} />}
          </>
        )}
      </div>
    </div>
  )
}
