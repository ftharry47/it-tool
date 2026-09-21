import { Link, useSearchParams } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery } from '@tanstack/react-query'
import { fetchWithToken } from '../../api/client'
import { Loading } from '../../components/ui/Loading'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { formatDate } from '../../lib/date'

interface QueueTicket {
  id: string
  number: string
  title: string | null
  status: string
  createdAt: string | null
  slaStatus: string | null
}

interface AgentQueueResponse {
  incidents: QueueTicket[]
  serviceRequests: QueueTicket[]
  problems: QueueTicket[]
  changes: QueueTicket[]
}

const SECTIONS: { key: keyof AgentQueueResponse; label: string; base: string }[] = [
  { key: 'incidents', label: 'Incidents', base: '/dashboard/incidents' },
  { key: 'serviceRequests', label: 'Service Requests', base: '/dashboard/service-requests' },
  { key: 'problems', label: 'Problems', base: '/dashboard/problems' },
  { key: 'changes', label: 'Changes', base: '/dashboard/changes' },
]

const SLA_TONE: Record<string, string> = {
  ON_TRACK: 'text-emerald-600 dark:text-emerald-400',
  AT_RISK: 'text-amber-600 dark:text-amber-400',
  BREACHED: 'text-red-600 dark:text-red-400',
}

/** Admin drill-down from "Workload per Agent": one agent's open queue, all types. */
export function AgentQueue() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const [searchParams] = useSearchParams()
  const agentId = searchParams.get('agentId')

  const queueQuery = useQuery<AgentQueueResponse>({
    queryKey: ['agent-queue', agentId],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, `/api/v1/reports/agent-queue?agentId=${agentId}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && !!agentId,
    refetchInterval: 30_000,
  })

  if (!agentId) {
    return (
      <div className="min-h-full bg-background p-6 text-foreground">
        <p className="text-sm text-muted-foreground">
          No agent selected. Open this view from the Dashboard's "Workload per Agent" section.
        </p>
      </div>
    )
  }

  const data = queueQuery.data
  const total = data
    ? data.incidents.length + data.serviceRequests.length + data.problems.length + data.changes.length
    : 0

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-5xl space-y-6">
        <div>
          <Link to="/dashboard" className="text-sm text-muted-foreground hover:text-foreground hover:underline">
            ← Dashboard
          </Link>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight">Agent Queue</h1>
          <p className="text-sm text-muted-foreground">{total} open item{total === 1 ? '' : 's'} across all work types.</p>
        </div>

        {queueQuery.isLoading && <Loading />}
        {queueQuery.error && (
          <p className="text-sm text-red-600 dark:text-red-400">Could not load the agent's queue.</p>
        )}

        {data && SECTIONS.map(({ key, label, base }) => (
          <div key={key} className="rounded-xl border border-border bg-card p-4 shadow-sm">
            <h3 className="mb-2 text-sm font-medium text-muted-foreground">
              {label} <span className="text-foreground">({data[key].length})</span>
            </h3>
            {data[key].length === 0 ? (
              <p className="text-sm text-muted-foreground">Nothing open.</p>
            ) : (
              <ul className="divide-y divide-border">
                {data[key].map((t) => (
                  <li key={t.id} className="flex items-center gap-3 py-2 text-sm">
                    <Link to={`${base}/${t.id}`} className="w-28 shrink-0 font-medium hover:underline">
                      {t.number}
                    </Link>
                    <span className="min-w-0 flex-1 truncate">{t.title ?? '—'}</span>
                    <StatusBadge status={t.status} />
                    {t.slaStatus && (
                      <span className={`w-20 text-right text-xs font-medium ${SLA_TONE[t.slaStatus] ?? 'text-muted-foreground'}`}>
                        {formatSla(t.slaStatus)}
                      </span>
                    )}
                    <span className="w-24 text-right text-xs text-muted-foreground">{formatDate(t.createdAt)}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}

function formatSla(s: string) {
  return s === 'ON_TRACK' ? 'On track' : s === 'AT_RISK' ? 'At risk' : s === 'BREACHED' ? 'Breached' : s
}
