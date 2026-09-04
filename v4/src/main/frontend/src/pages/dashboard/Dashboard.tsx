import { Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery } from '@tanstack/react-query'
import { AlertCircle } from 'lucide-react'
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
}

function MetricCard({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="rounded-xl border border-border bg-card p-4 text-center shadow-sm">
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
  if (isLoading) return <Loading />
  if (error) return <ErrorFallback error={error} message="Could not load priority breakdown." onRetry={onRetry} />
  if (!data) return <Loading />
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
  { key: 'createdAt', header: 'Created', render: (row: IncidentSummary) => new Date(row.createdAt).toLocaleDateString() },
]

export function Dashboard() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const { currentUser } = useAuth()

  const slaQuery = useQuery<SlaCompliance>({
    queryKey: ['dashboard', 'sla-compliance'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/reports/sla-compliance')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
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
    enabled: !!account,
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
    enabled: !!account,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const attentionQuery = useQuery<IncidentSummary[]>({
    queryKey: ['dashboard', 'attention', currentUser?.id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, `/api/v1/incidents?status=${ACTIVE_STATUSES}&limit=5`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!currentUser,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const assignedQuery = useQuery<IncidentSummary[]>({
    queryKey: ['dashboard', 'assigned', currentUser?.id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, `/api/v1/incidents?status=${ACTIVE_STATUSES}&assigneeId=${currentUser!.id}&limit=5`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!currentUser,
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

  const isLoading = slaQuery.isLoading || ticketsQuery.isLoading
  const hasError = slaQuery.error || ticketsQuery.error

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-7xl space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
            <p className="text-sm text-muted-foreground">Welcome back, {currentUser.displayName ?? currentUser.email}.</p>
          </div>
          <Link
            to="/dashboard/notifications"
            className="inline-flex items-center gap-2 rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-muted"
          >
            <AlertCircle className="h-4 w-4" />
            Notification Preferences
          </Link>
        </div>

        {isLoading && <Loading />}
        {hasError && <ErrorFallback error={slaQuery.error ?? ticketsQuery.error} message="Could not load dashboard metrics." onRetry={() => { slaQuery.refetch(); ticketsQuery.refetch() }} />}

        {!isLoading && !hasError && (
          <div className="space-y-6">
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

            <PriorityView data={priorityQuery.data} isLoading={priorityQuery.isLoading} error={priorityQuery.error} onRetry={() => priorityQuery.refetch()} />

            <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
              <div className="space-y-2">
                <h3 className="text-sm font-medium text-muted-foreground">Incidents needing attention</h3>
                <DataTable<IncidentSummary>
                  caption="Open incidents needing attention"
                  columns={INCIDENT_COLUMNS}
                  data={attentionQuery.data ?? []}
                  getRowKey={(row) => row.id}
                  isLoading={attentionQuery.isLoading}
                  emptyText="No open incidents."
                />
              </div>
              <div className="space-y-2">
                <h3 className="text-sm font-medium text-muted-foreground">Assigned to me</h3>
                <DataTable<IncidentSummary>
                  caption="Incidents assigned to you"
                  columns={INCIDENT_COLUMNS}
                  data={assignedQuery.data ?? []}
                  getRowKey={(row) => row.id}
                  isLoading={assignedQuery.isLoading}
                  emptyText="No incidents assigned to you."
                />
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
