import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { AccountInfo, IPublicClientApplication } from '@azure/msal-browser'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { SlaCountdown } from '../../components/ui/SlaCountdown'
import { CreateSlaPolicyForm } from '../../components/sla/CreateSlaPolicyForm'
import { DataTable } from '../../components/ui/DataTable'
import { DateInput } from '../../components/ui/DateInput'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { Loading } from '../../components/ui/Loading'
import { ArrowLeft } from 'lucide-react'
import { useSmartBack } from '../../lib/useSmartBack'

type BreachStatus = 'ON_TRACK' | 'AT_RISK' | 'BREACHED'

interface SlaInstanceRow {
  id: string
  entityKind: 'INCIDENT' | 'SERVICE_REQUEST' | 'PROBLEM' | 'CHANGE' | null
  incidentId: string | null
  incidentNumber: number | null
  incidentTitle: string | null
  incidentPriority: string | null
  serviceRequestId: string | null
  serviceRequestNumber: string | null
  serviceRequestTitle: string | null
  serviceRequestPriority: string | null
  fulfillerName: string | null
  problemId: string | null
  problemNumber: string | null
  problemTitle: string | null
  changeId: string | null
  changeNumber: string | null
  changeTitle: string | null
  policyName: string
  workflowType: string | null
  responseDueAt: string | null
  resolutionDueAt: string | null
  responseMetAt: string | null
  resolutionMetAt: string | null
  pausedAt: string | null
  totalPausedMinutes: number
  breachStatus: BreachStatus
}

interface PriorityOption {
  id: string
  name: string
}

interface CalendarOption {
  id: string
  name: string
}

interface SlaPolicy {
  id: string
  name: string
  appliesTo: 'INCIDENT' | 'REQUEST' | 'PROBLEM' | 'CHANGE'
  priorityFilter: string | null
  workflowType: string | null
  responseTargetMinutes: number
  resolutionTargetMinutes: number
  businessHoursCalendar: CalendarOption | null
}

interface PolicyEditState {
  responseTargetMinutes: number
  resolutionTargetMinutes: number
  businessHoursCalendarId: string | null
}

interface SlaMonthlyPoint {
  month: string
  total: number
  breached: number
  compliancePercent: number
}

interface SlaOverall {
  total: number
  breached: number
  compliancePercent: number
}

interface SlaBreakdown {
  period: string
  overall: SlaOverall
  byAgent: { agentId: string | null; agentName: string; total: number; breached: number; compliancePercent: number }[]
  byTeam: { teamName: string; total: number; breached: number; compliancePercent: number }[]
}

/** Admin-only: current-month SLA compliance broken down by team and agent. */
function SlaComplianceBreakdown({ instance, account }: { instance: IPublicClientApplication; account: AccountInfo | undefined }) {
  const breakdownQuery = useQuery<SlaBreakdown>({
    queryKey: ['reports', 'sla-compliance', 'breakdown'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/reports/sla-compliance/breakdown')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const complianceCell = (row: { total: number; breached: number; compliancePercent: number }) => (
    <>
      <td className="px-3 py-2 text-sm">{row.total}</td>
      <td className={`px-3 py-2 text-sm ${row.breached > 0 ? 'font-medium text-red-700' : ''}`}>{row.breached}</td>
      <td className="px-3 py-2 text-sm font-medium">{row.compliancePercent}%</td>
    </>
  )

  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold tracking-tight">Compliance Breakdown</h2>
          <p className="text-sm text-muted-foreground">
            {breakdownQuery.data ? `Current month (${breakdownQuery.data.period})` : 'Current month'} — overall{' '}
            {breakdownQuery.data ? `${breakdownQuery.data.overall.compliancePercent}%` : '…'}
          </p>
        </div>
      </div>
      {breakdownQuery.isLoading ? (
        <Loading />
      ) : breakdownQuery.error ? (
        <ErrorFallback error={breakdownQuery.error} message="Could not load the SLA breakdown." onRetry={() => breakdownQuery.refetch()} />
      ) : (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <div>
            <h3 className="mb-2 text-sm font-medium text-muted-foreground">By Team</h3>
            <table className="w-full text-left">
              <thead>
                <tr className="border-b border-border text-xs uppercase text-muted-foreground">
                  <th className="px-3 py-2">Team</th>
                  <th className="px-3 py-2">SLAs</th>
                  <th className="px-3 py-2">Breached</th>
                  <th className="px-3 py-2">Compliance</th>
                </tr>
              </thead>
              <tbody>
                {(breakdownQuery.data?.byTeam ?? []).map((row) => (
                  <tr key={row.teamName} className="border-b border-border last:border-0">
                    <td className="px-3 py-2 text-sm font-medium">{row.teamName}</td>
                    {complianceCell(row)}
                  </tr>
                ))}
                {breakdownQuery.data?.byTeam.length === 0 && (
                  <tr><td colSpan={4} className="px-3 py-4 text-center text-sm text-muted-foreground">No SLA instances this month.</td></tr>
                )}
              </tbody>
            </table>
          </div>
          <div>
            <h3 className="mb-2 text-sm font-medium text-muted-foreground">By Agent</h3>
            <table className="w-full text-left">
              <thead>
                <tr className="border-b border-border text-xs uppercase text-muted-foreground">
                  <th className="px-3 py-2">Agent</th>
                  <th className="px-3 py-2">SLAs</th>
                  <th className="px-3 py-2">Breached</th>
                  <th className="px-3 py-2">Compliance</th>
                </tr>
              </thead>
              <tbody>
                {(breakdownQuery.data?.byAgent ?? []).map((row) => (
                  <tr key={row.agentId ?? 'unassigned'} className="border-b border-border last:border-0">
                    <td className="px-3 py-2 text-sm font-medium">{row.agentName}</td>
                    {complianceCell(row)}
                  </tr>
                ))}
                {breakdownQuery.data?.byAgent.length === 0 && (
                  <tr><td colSpan={4} className="px-3 py-4 text-center text-sm text-muted-foreground">No SLA instances this month.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}

/** Plain-language explainer of how the SLA engine works, for agents and admins. */
function HowSlaWorks() {
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm space-y-3">
      <h2 className="text-lg font-semibold tracking-tight">How SLA Works</h2>
      <div className="grid grid-cols-1 gap-4 text-sm text-muted-foreground md:grid-cols-2">
        <div className="space-y-2">
          <p><span className="font-medium text-foreground">Policies.</span> Each SLA policy applies to one ticket type — Incident, Request, Problem, or Change — with a response and a resolution target in business minutes, counted on the policy's business calendar. An optional priority filter (risk level for changes) limits which tickets it governs.</p>
          <p><span className="font-medium text-foreground">Clocks.</span> Response starts when the ticket is created and is met when it is first worked on — an incident leaves NEW, a problem enters INVESTIGATING, a change is APPROVED. Resolution is met when the ticket reaches a closed state (Resolved/Closed for incidents and problems, Completed/Closed for changes, Fulfilled for requests).</p>
        </div>
        <div className="space-y-2">
          <p><span className="font-medium text-foreground">Breach states.</span> ON_TRACK → AT_RISK at 75% of the resolution clock → BREACHED once the due time passes. Tickets paused on the customer hold their clock; deleted tickets disappear from all SLA views.</p>
          <p><span className="font-medium text-foreground">Escalations.</span> Policies can carry escalation tiers that fire on a response breach, a resolution breach, or when a ticket is stuck in a status too long. Tiers notify a role (and always notify admins); for incidents they can also reassign the ticket to a different support tier. Changing a policy notifies everyone who currently owns tickets under it.</p>
        </div>
      </div>
    </div>
  )
}

const SLA_COLORS = ['var(--chart-2)', 'var(--chart-1)']

function SlaTrendCharts() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const [view, setView] = useState<'monthly' | 'overall'>('monthly')

  const monthlyQuery = useQuery<SlaMonthlyPoint[]>({
    queryKey: ['reports', 'sla-trend', 'monthly', 12],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/reports/sla-trend/monthly?months=12')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const overallQuery = useQuery<SlaOverall>({
    queryKey: ['reports', 'sla-trend', 'overall'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/reports/sla-trend/overall')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold tracking-tight">SLA Trends</h2>
        <div className="flex gap-2">
          <button
            onClick={() => setView('monthly')}
            className={`rounded-md px-3 py-1.5 text-sm font-medium transition ${view === 'monthly' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-muted'}`}
          >
            Monthly
          </button>
          <button
            onClick={() => setView('overall')}
            className={`rounded-md px-3 py-1.5 text-sm font-medium transition ${view === 'overall' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-muted'}`}
          >
            Overall
          </button>
        </div>
      </div>

      {view === 'monthly' && (
        <div>
          {monthlyQuery.isLoading ? (
            <Loading />
          ) : monthlyQuery.error ? (
            <ErrorFallback error={monthlyQuery.error} message="Could not load monthly SLA trend." onRetry={() => monthlyQuery.refetch()} />
          ) : !monthlyQuery.data || monthlyQuery.data.length === 0 ? (
            <div className="flex h-72 items-center justify-center">
              <p className="text-sm text-muted-foreground">No SLA data for the selected period.</p>
            </div>
          ) : (
            <div className="h-72">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={monthlyQuery.data}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="month" tick={{ fontSize: 12 }} />
                  <YAxis domain={[0, 100]} />
                  <Tooltip />
                  <Line type="monotone" dataKey="compliancePercent" name="Compliance %" stroke={SLA_COLORS[0]} strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>
      )}

      {view === 'overall' && (
        <div>
          {overallQuery.isLoading ? (
            <Loading />
          ) : overallQuery.error ? (
            <ErrorFallback error={overallQuery.error} message="Could not load overall SLA summary." onRetry={() => overallQuery.refetch()} />
          ) : overallQuery.data ? (
            <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
              <div className="h-64">
                {overallQuery.data.total === 0 ? (
                  <div className="flex h-full items-center justify-center">
                    <p className="text-sm text-muted-foreground">No SLA instances yet.</p>
                  </div>
                ) : (
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={[
                          { name: 'Compliant', value: Math.max(0, overallQuery.data.total - overallQuery.data.breached) },
                          { name: 'Breached', value: overallQuery.data.breached },
                        ].filter((d) => d.value > 0)}
                        dataKey="value"
                        nameKey="name"
                        outerRadius={80}
                        label
                      >
                        {['Compliant', 'Breached'].map((_, i) => (
                          <Cell key={i} fill={SLA_COLORS[i % SLA_COLORS.length]} />
                        ))}
                      </Pie>
                      <Tooltip />
                      <Legend />
                    </PieChart>
                  </ResponsiveContainer>
                )}
              </div>
              <div className="flex flex-col justify-center gap-4 text-center">
                <div>
                  <p className="text-3xl font-bold">{overallQuery.data.compliancePercent}%</p>
                  <p className="text-sm text-muted-foreground">Overall compliance</p>
                </div>
                <div>
                  <p className="text-3xl font-bold">{overallQuery.data.total}</p>
                  <p className="text-sm text-muted-foreground">Total SLA instances</p>
                </div>
              </div>
            </div>
          ) : null}
        </div>
      )}
    </div>
  )
}

const BREACH_OPTIONS: { value: BreachStatus; label: string }[] = [
  { value: 'ON_TRACK', label: 'On Track' },
  { value: 'AT_RISK', label: 'At Risk' },
  { value: 'BREACHED', label: 'Breached' },
]

function buildQueryString(filters: {
  breachStatus: Set<BreachStatus>
  priority: string
  dateFrom: string
  dateTo: string
}, mine: boolean) {
  const params = new URLSearchParams()
  Array.from(filters.breachStatus).forEach((s) => params.append('breachStatus', s))
  if (filters.priority) params.set('priority', filters.priority)
  if (filters.dateFrom) params.set('dateFrom', `${filters.dateFrom}T00:00:00Z`)
  if (filters.dateTo) params.set('dateTo', `${filters.dateTo}T23:59:59Z`)
  if (mine) params.set('mine', 'true')
  const qs = params.toString()
  return qs ? `?${qs}` : ''
}

function BreachStatusBadge({ status }: { status: BreachStatus }) {
  const styles = {
    ON_TRACK: 'bg-green-500/10 text-green-700',
    AT_RISK: 'bg-amber-500/10 text-amber-700',
    BREACHED: 'bg-red-500/10 text-red-700',
  }
  return (
    <span className={`inline-flex rounded px-2 py-0.5 text-xs font-medium ${styles[status]}`}>
      {status.replace('_', ' ')}
    </span>
  )
}

export function SlaDetails() {
  const smartBack = useSmartBack('/dashboard')
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const { currentUser } = useAuth()
  const queryClient = useQueryClient()
  const canEdit = currentUser?.roles.some((r) => r === 'ADMIN' || r === 'SUPER_ADMIN') ?? false

  const [filters, setFilters] = useState({
    breachStatus: new Set<BreachStatus>(['ON_TRACK', 'AT_RISK', 'BREACHED']),
    priority: '',
    dateFrom: '',
    dateTo: '',
  })

  // Deep links (e.g. SLA compliance report → breached slice) seed the filters.
  const [searchParams] = useSearchParams()
  useEffect(() => {
    const breach = searchParams.get('breachStatus')
    const priority = searchParams.get('priority')
    if (breach || priority) {
      setFilters((prev) => ({
        ...prev,
        breachStatus: breach
          ? new Set(breach.split(',').filter((s): s is BreachStatus =>
              ['ON_TRACK', 'AT_RISK', 'BREACHED'].includes(s)))
          : prev.breachStatus,
        priority: priority ?? prev.priority,
      }))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const [editing, setEditing] = useState<Record<string, PolicyEditState>>({})
  const [showCreate, setShowCreate] = useState(false)
  const [createFormDirty, setCreateFormDirty] = useState(false)
  const [confirmCloseCreate, setConfirmCloseCreate] = useState(false)
  const [confirmCancelPolicyId, setConfirmCancelPolicyId] = useState<string | null>(null)
  const [slaTab, setSlaTab] = useState<'INCIDENT' | 'SERVICE_REQUEST'>('INCIDENT')

  const mine = !canEdit

  const instancesQuery = useQuery<SlaInstanceRow[]>({
    queryKey: ['sla-instances', mine, Array.from(filters.breachStatus).sort().join(','), filters.priority, filters.dateFrom, filters.dateTo],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, `/api/v1/sla-instances${buildQueryString(filters, mine)}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
    refetchInterval: 60_000,
    staleTime: 0,
  })

  const mySlaSummary = useMemo(() => {
    const rows = instancesQuery.data ?? []
    const total = rows.length
    const breached = rows.filter((r) => r.breachStatus === 'BREACHED').length
    const compliancePercent = total === 0 ? 100 : Math.round(((total - breached) * 100.0) / total)
    return { total, breached, compliancePercent }
  }, [instancesQuery.data])

  const prioritiesQuery = useQuery<PriorityOption[]>({
    queryKey: ['priorities'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/incidents/priorities')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const policiesQuery = useQuery<SlaPolicy[]>({
    queryKey: ['sla-policies'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/sla-policies')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && canEdit,
  })

  const calendarsQuery = useQuery<CalendarOption[]>({
    queryKey: ['business-calendars'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/business-calendars')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && canEdit,
  })

  const updatePolicy = useMutation({
    mutationFn: async ({ policy, edit }: { policy: SlaPolicy; edit: PolicyEditState }) => {
      const body = {
        name: policy.name,
        appliesTo: policy.appliesTo,
        priorityFilter: policy.priorityFilter,
        workflowType: policy.workflowType,
        responseTargetMinutes: edit.responseTargetMinutes,
        resolutionTargetMinutes: edit.resolutionTargetMinutes,
        businessHoursCalendarId: edit.businessHoursCalendarId || null,
      }
      const res = await fetchWithToken(instance, account, `/api/v1/sla-policies/${policy.id}`, {
        method: 'PUT',
        body: JSON.stringify(body),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sla-policies'] })
      queryClient.invalidateQueries({ queryKey: ['sla-instances'] })
    },
  })

  function toggleStatus(status: BreachStatus) {
    setFilters((prev) => {
      const next = new Set(prev.breachStatus)
      if (next.has(status)) next.delete(status)
      else next.add(status)
      return { ...prev, breachStatus: next }
    })
  }

  function resetFilters() {
    setFilters({
      breachStatus: new Set<BreachStatus>(['ON_TRACK', 'AT_RISK', 'BREACHED']),
      priority: '',
      dateFrom: '',
      dateTo: '',
    })
  }

  const ticketRef = (row: SlaInstanceRow): { to: string; label: string } | null =>
    row.incidentId
      ? { to: `/dashboard/incidents/${row.incidentId}`, label: `#${row.incidentNumber}` }
      : row.serviceRequestId
        ? { to: `/dashboard/service-requests/${row.serviceRequestId}`, label: row.serviceRequestNumber ?? '—' }
        : row.problemId
          ? { to: `/dashboard/problems/${row.problemId}`, label: row.problemNumber ?? '—' }
          : row.changeId
            ? { to: `/dashboard/changes/${row.changeId}`, label: row.changeNumber ?? '—' }
            : null

  const clockColumns = [
    { key: 'policyName', header: 'Policy' },
    {
      key: 'responseDueAt',
      header: 'Response Due',
      render: (row: SlaInstanceRow) => (
        <SlaCountdown
          breachStatus={row.breachStatus}
          dueAt={row.responseDueAt}
          metAt={row.responseMetAt}
          pausedAt={row.pausedAt}
        />
      ),
    },
    {
      key: 'resolutionDueAt',
      header: 'Resolution Due',
      render: (row: SlaInstanceRow) => (
        <SlaCountdown
          breachStatus={row.breachStatus}
          resolutionDueAt={row.resolutionDueAt}
          resolutionMetAt={row.resolutionMetAt}
          pausedAt={row.pausedAt}
        />
      ),
    },
    {
      key: 'breachStatus',
      header: 'Breach Status',
      render: (row: SlaInstanceRow) => <BreachStatusBadge status={row.breachStatus} />,
    },
    {
      key: 'pausedAt',
      header: 'Paused',
      render: (row: SlaInstanceRow) => (row.pausedAt ? 'Yes' : '—'),
    },
  ]

  const ticketColumn = {
    key: 'ticketNumber',
    header: '#',
    render: (row: SlaInstanceRow) => {
      const ref = ticketRef(row)
      return ref ? (
        <Link to={ref.to} className="font-medium hover:underline">
          {ref.label}
        </Link>
      ) : (
        '—'
      )
    },
  }

  const incidentColumns = [
    ticketColumn,
    { key: 'incidentTitle', header: 'Title', render: (row: SlaInstanceRow) => row.incidentTitle ?? '—' },
    { key: 'incidentPriority', header: 'Priority', render: (row: SlaInstanceRow) => row.incidentPriority ?? '—' },
    ...clockColumns,
  ]

  const WORKFLOW_BADGE: Record<string, string> = {
    INSTANT: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300',
    SOFTWARE: 'bg-sky-100 text-sky-800 dark:bg-sky-900/40 dark:text-sky-300',
    FULL: 'bg-violet-100 text-violet-800 dark:bg-violet-900/40 dark:text-violet-300',
  }

  const requestColumns = [
    ticketColumn,
    { key: 'serviceRequestTitle', header: 'Catalog Item', render: (row: SlaInstanceRow) => row.serviceRequestTitle ?? '—' },
    { key: 'serviceRequestPriority', header: 'Priority', render: (row: SlaInstanceRow) => row.serviceRequestPriority ?? '—' },
    {
      key: 'workflowType',
      header: 'Workflow',
      render: (row: SlaInstanceRow) =>
        row.workflowType ? (
          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${WORKFLOW_BADGE[row.workflowType] ?? 'bg-muted text-muted-foreground'}`}>
            {row.workflowType}
          </span>
        ) : (
          '—'
        ),
    },
    { key: 'fulfillerName', header: 'Fulfiller', render: (row: SlaInstanceRow) => row.fulfillerName ?? '—' },
    ...clockColumns,
  ]

  const allInstances = instancesQuery.data ?? []
  const incidentRows = allInstances.filter((r) => r.entityKind === 'INCIDENT')
  const requestRows = allInstances.filter((r) => r.entityKind === 'SERVICE_REQUEST')
  const activeRows = slaTab === 'INCIDENT' ? incidentRows : requestRows
  const activeColumns = slaTab === 'INCIDENT' ? incidentColumns : requestColumns

  function startEdit(policy: SlaPolicy) {
    setEditing((prev) => ({
      ...prev,
      [policy.id]: {
        responseTargetMinutes: policy.responseTargetMinutes,
        resolutionTargetMinutes: policy.resolutionTargetMinutes,
        businessHoursCalendarId: policy.businessHoursCalendar?.id ?? null,
      },
    }))
  }

  function isPolicyEditDirty(policy: SlaPolicy, edit: PolicyEditState): boolean {
    return (
      edit.responseTargetMinutes !== policy.responseTargetMinutes ||
      edit.resolutionTargetMinutes !== policy.resolutionTargetMinutes ||
      edit.businessHoursCalendarId !== (policy.businessHoursCalendar?.id ?? null)
    )
  }

  function cancelEdit(policy: SlaPolicy) {
    const edit = editing[policy.id]
    const clear = () =>
      setEditing((prev) => {
        const next = { ...prev }
        delete next[policy.id]
        return next
      })
    if (edit && isPolicyEditDirty(policy, edit)) setConfirmCancelPolicyId(policy.id)
    else clear()
  }

  function updateEditField(id: string, field: keyof PolicyEditState, value: string | number) {
    setEditing((prev) => ({
      ...prev,
      [id]: {
        ...prev[id],
        [field]:
          field === 'businessHoursCalendarId'
            ? (value as string) || null
            : Number(value),
      },
    }))
  }

  const isLoading = instancesQuery.isLoading || prioritiesQuery.isLoading
  const hasError = instancesQuery.error || prioritiesQuery.error

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <ConfirmDialog
        open={confirmCloseCreate}
        title="Discard unsaved changes?"
        description="You have unsaved changes in the new policy form that will be lost."
        confirmLabel="Discard"
        destructive
        onConfirm={() => {
          setConfirmCloseCreate(false)
          setShowCreate(false)
          setCreateFormDirty(false)
        }}
        onCancel={() => setConfirmCloseCreate(false)}
      />
      <ConfirmDialog
        open={confirmCancelPolicyId !== null}
        title="Discard unsaved changes?"
        description="You have unsaved changes to this policy that will be lost."
        confirmLabel="Discard"
        destructive
        onConfirm={() => {
          const id = confirmCancelPolicyId
          setConfirmCancelPolicyId(null)
          if (id) {
            setEditing((prev) => {
              const next = { ...prev }
              delete next[id]
              return next
            })
          }
        }}
        onCancel={() => setConfirmCancelPolicyId(null)}
      />
      <div className="mx-auto max-w-6xl space-y-6">
        <div>
          <div className="flex items-center gap-3">
            <button
                        onClick={smartBack}
                        className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      >
                        <ArrowLeft className="h-4 w-4" />
                        Back
                      </button>
            <div>
              <h1 className="text-2xl font-semibold tracking-tight">{canEdit ? 'SLA Details' : 'My SLA Performance'}</h1>
              <p className="text-sm text-muted-foreground">
                          {canEdit
                            ? 'Monitor and manage active SLA instances and policies.'
                            : 'View the SLA status of incidents assigned to you and service requests you submitted.'}
                        </p>
            </div>
          </div>
        </div>

        <HowSlaWorks />

        {canEdit && <SlaComplianceBreakdown instance={instance} account={account} />}

        {canEdit ? (
          <SlaTrendCharts />
        ) : (
          <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
            <h2 className="mb-4 text-lg font-semibold tracking-tight">My SLA Summary</h2>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
              <div className="rounded-xl border border-border bg-card p-4 text-center shadow-sm">
                <p className="text-2xl font-bold">{mySlaSummary.compliancePercent}%</p>
                <p className="text-xs text-muted-foreground">My compliance</p>
              </div>
              <div className="rounded-xl border border-border bg-card p-4 text-center shadow-sm">
                <p className="text-2xl font-bold">{mySlaSummary.total}</p>
                <p className="text-xs text-muted-foreground">My SLAs</p>
              </div>
              <div className="rounded-xl border border-border bg-card p-4 text-center shadow-sm">
                <p className="text-2xl font-bold">{mySlaSummary.breached}</p>
                <p className="text-xs text-muted-foreground">My breached SLAs</p>
              </div>
            </div>
          </div>
        )}

        <div className="rounded-xl border border-border bg-card p-4 shadow-sm space-y-4">
          <div className="flex flex-wrap items-end gap-4">
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">Breach Status</label>
              <div className="flex flex-wrap gap-3">
                {BREACH_OPTIONS.map((opt) => (
                  <label key={opt.value} className="inline-flex items-center gap-1.5 text-sm">
                    <input
                      type="checkbox"
                      checked={filters.breachStatus.has(opt.value)}
                      onChange={() => toggleStatus(opt.value)}
                      className="h-4 w-4 rounded border-border"
                    />
                    {opt.label}
                  </label>
                ))}
              </div>
            </div>

            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">Priority</label>
              <select
                value={filters.priority}
                onChange={(e) => setFilters((prev) => ({ ...prev, priority: e.target.value }))}
                className="rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">All priorities</option>
                {prioritiesQuery.data?.map((p) => (
                  <option key={p.id} value={p.name}>
                    {p.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">From</label>
              <DateInput
                value={filters.dateFrom}
                onChange={(e) => setFilters((prev) => ({ ...prev, dateFrom: e.target.value }))}
                className="border-border px-3 py-2 text-sm"
              />
            </div>

            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">To</label>
              <DateInput
                value={filters.dateTo}
                onChange={(e) => setFilters((prev) => ({ ...prev, dateTo: e.target.value }))}
                className="border-border px-3 py-2 text-sm"
              />
            </div>

            <button
              onClick={resetFilters}
              className="rounded-md border border-border px-3 py-2 text-sm font-medium transition hover:bg-muted"
            >
              Reset
            </button>
          </div>
        </div>

        {isLoading && <Loading />}
        {hasError && (
          <ErrorFallback
            error={instancesQuery.error ?? prioritiesQuery.error}
            message="Could not load SLA details."
            onRetry={() => {
              instancesQuery.refetch()
              prioritiesQuery.refetch()
            }}
          />
        )}

        {!isLoading && !hasError && (
          <div className="space-y-3">
            <div className="inline-flex rounded-md border border-border bg-muted p-0.5">
              {([
                { key: 'INCIDENT' as const, label: `Incidents (${incidentRows.length})` },
                { key: 'SERVICE_REQUEST' as const, label: `Service Requests (${requestRows.length})` },
              ]).map((t) => (
                <button
                  key={t.key}
                  onClick={() => setSlaTab(t.key)}
                  className={`rounded px-3 py-1.5 text-sm font-medium transition ${
                    slaTab === t.key
                      ? 'bg-background text-foreground shadow-sm'
                      : 'text-muted-foreground hover:text-foreground'
                  }`}
                >
                  {t.label}
                </button>
              ))}
            </div>
            <DataTable<SlaInstanceRow>
              data={activeRows}
              columns={activeColumns}
              getRowKey={(row) => row.id}
              emptyText={`No ${slaTab === 'INCIDENT' ? 'incident' : 'service request'} SLA instances match the selected filters.`}
            />
          </div>
        )}

        {canEdit && (
          <div className="space-y-4 rounded-xl border border-border bg-card p-4 shadow-sm">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-lg font-semibold tracking-tight">SLA Policies</h2>
                <p className="text-sm text-muted-foreground">Edit response/resolution targets and business calendars.</p>
              </div>
              <button
                onClick={() => {
                  if (showCreate && createFormDirty) setConfirmCloseCreate(true)
                  else setShowCreate((v) => !v)
                }}
                className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:bg-primary/90"
              >
                {showCreate ? 'Cancel' : 'Create Policy'}
              </button>
            </div>

            {showCreate && (
              <CreateSlaPolicyForm
                instance={instance}
                account={account}
                priorities={prioritiesQuery.data ?? []}
                calendars={calendarsQuery.data ?? []}
                onCreated={() => {
                  setShowCreate(false)
                  setCreateFormDirty(false)
                }}
                onDirtyChange={setCreateFormDirty}
              />
            )}

            {policiesQuery.isLoading && <Loading />}
            {policiesQuery.error && (
              <ErrorFallback
                error={policiesQuery.error}
                message="Could not load SLA policies."
                onRetry={() => policiesQuery.refetch()}
              />
            )}

            {!policiesQuery.isLoading && !policiesQuery.error && (
              <DataTable<SlaPolicy>
                data={policiesQuery.data ?? []}
                getRowKey={(row) => row.id}
                emptyText="No SLA policies found."
                columns={[
                  { key: 'name', header: 'Policy' },
                  { key: 'appliesTo', header: 'Applies To' },
                  { key: 'priorityFilter', header: 'Priority Filter' },
                  { key: 'workflowType', header: 'Workflow' },
                  {
                    key: 'responseTargetMinutes',
                    header: 'Response (min)',
                    render: (policy) => {
                      const edit = editing[policy.id]
                      if (!edit) return policy.responseTargetMinutes
                      return (
                        <input
                          type="number"
                          value={edit.responseTargetMinutes}
                          onChange={(e) => updateEditField(policy.id, 'responseTargetMinutes', e.target.value)}
                          className="w-24 rounded-md border border-border bg-background px-2 py-1 text-sm"
                        />
                      )
                    },
                  },
                  {
                    key: 'resolutionTargetMinutes',
                    header: 'Resolution (min)',
                    render: (policy) => {
                      const edit = editing[policy.id]
                      if (!edit) return policy.resolutionTargetMinutes
                      return (
                        <input
                          type="number"
                          value={edit.resolutionTargetMinutes}
                          onChange={(e) => updateEditField(policy.id, 'resolutionTargetMinutes', e.target.value)}
                          className="w-24 rounded-md border border-border bg-background px-2 py-1 text-sm"
                        />
                      )
                    },
                  },
                  {
                    key: 'businessHoursCalendar',
                    header: 'Business Calendar',
                    render: (policy) => {
                      const edit = editing[policy.id]
                      if (!edit) return policy.businessHoursCalendar?.name ?? '—'
                      return (
                        <select
                          value={edit.businessHoursCalendarId ?? ''}
                          onChange={(e) => updateEditField(policy.id, 'businessHoursCalendarId', e.target.value)}
                          className="rounded-md border border-border bg-background px-2 py-1 text-sm"
                        >
                          <option value="">None</option>
                          {calendarsQuery.data?.map((c) => (
                            <option key={c.id} value={c.id}>
                              {c.name}
                            </option>
                          ))}
                        </select>
                      )
                    },
                  },
                  {
                    key: 'actions',
                    header: 'Actions',
                    render: (policy) => {
                      const edit = editing[policy.id]
                      if (!edit) {
                        return (
                          <button
                            onClick={() => startEdit(policy)}
                            className="text-sm font-medium text-primary hover:underline"
                          >
                            Edit
                          </button>
                        )
                      }
                      return (
                        <div className="flex gap-2">
                          <button
                            onClick={() => updatePolicy.mutate({ policy, edit })}
                            disabled={updatePolicy.isPending}
                            className="rounded-md bg-primary px-2 py-1 text-xs font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-60"
                          >
                            {updatePolicy.isPending ? 'Saving…' : 'Save'}
                          </button>
                          <button
                            onClick={() => cancelEdit(policy)}
                            className="rounded-md border border-border px-2 py-1 text-xs font-medium transition hover:bg-muted"
                          >
                            Cancel
                          </button>
                        </div>
                      )
                    },
                  },
                ]}
              />
            )}
          </div>
        )}
      </div>
    </div>
  )
}
