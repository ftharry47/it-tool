import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { SlaCountdown } from '../../components/sla/SlaCountdown'
import { CreateSlaPolicyForm } from '../../components/sla/CreateSlaPolicyForm'
import { DataTable } from '../../components/ui/DataTable'
import { DateInput } from '../../components/ui/DateInput'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { Loading } from '../../components/ui/Loading'

type BreachStatus = 'ON_TRACK' | 'AT_RISK' | 'BREACHED'

interface SlaInstanceRow {
  id: string
  incidentId: string | null
  incidentNumber: number | null
  incidentTitle: string | null
  incidentPriority: string | null
  policyName: string
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
  appliesTo: 'INCIDENT' | 'REQUEST'
  priorityFilter: string | null
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
}) {
  const params = new URLSearchParams()
  filters.breachStatus.forEach((s) => params.append('breachStatus', s))
  if (filters.priority) params.set('priority', filters.priority)
  if (filters.dateFrom) params.set('dateFrom', `${filters.dateFrom}T00:00:00Z`)
  if (filters.dateTo) params.set('dateTo', `${filters.dateTo}T23:59:59Z`)
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

  const [editing, setEditing] = useState<Record<string, PolicyEditState>>({})
  const [showCreate, setShowCreate] = useState(false)
  const [createFormDirty, setCreateFormDirty] = useState(false)
  const [confirmCloseCreate, setConfirmCloseCreate] = useState(false)
  const [confirmCancelPolicyId, setConfirmCancelPolicyId] = useState<string | null>(null)

  const instancesQuery = useQuery<SlaInstanceRow[]>({
    queryKey: ['sla-instances', filters],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, `/api/v1/sla-instances${buildQueryString(filters)}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
    refetchInterval: 60_000,
    staleTime: 0,
  })

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

  const columns = [
    {
      key: 'incidentNumber',
      header: '#',
      render: (row: SlaInstanceRow) =>
        row.incidentId ? (
          <Link to={`/dashboard/incidents/${row.incidentId}`} className="font-medium hover:underline">
            #{row.incidentNumber}
          </Link>
        ) : (
          '—'
        ),
    },
    { key: 'incidentTitle', header: 'Title', render: (row: SlaInstanceRow) => row.incidentTitle ?? '—' },
    { key: 'incidentPriority', header: 'Priority' },
    { key: 'policyName', header: 'Policy' },
    {
      key: 'responseDueAt',
      header: 'Response Due',
      render: (row: SlaInstanceRow) => <SlaCountdown dueAt={row.responseDueAt} pausedAt={row.pausedAt} />,
    },
    {
      key: 'resolutionDueAt',
      header: 'Resolution Due',
      render: (row: SlaInstanceRow) => <SlaCountdown dueAt={row.resolutionDueAt} pausedAt={row.pausedAt} />,
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
      <div className="mx-auto max-w-7xl space-y-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">SLA Details</h1>
          <p className="text-sm text-muted-foreground">Monitor and manage active SLA instances and policies.</p>
        </div>

        <SlaTrendCharts />

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
          <DataTable<SlaInstanceRow>
            data={instancesQuery.data ?? []}
            columns={columns}
            getRowKey={(row) => row.id}
            emptyText="No SLA instances match the selected filters."
          />
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
