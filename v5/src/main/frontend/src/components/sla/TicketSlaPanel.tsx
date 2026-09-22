import { useQuery } from '@tanstack/react-query'
import { useMsal } from '@azure/msal-react'
import { fetchWithToken } from '../../api/client'
import { SlaCountdown } from '../ui/SlaCountdown'
import { formatDateTime } from '../../lib/date'

export type SlaEntityType = 'incident' | 'service-request' | 'problem' | 'change'

const PARAM: Record<SlaEntityType, string> = {
  incident: 'incidentId',
  'service-request': 'serviceRequestId',
  problem: 'problemId',
  change: 'changeId',
}

interface TicketSlaInstance {
  id: string
  entityKind: string
  policyName: string | null
  responseDueAt: string | null
  resolutionDueAt: string | null
  responseMetAt: string | null
  resolutionMetAt: string | null
  pausedAt: string | null
  totalPausedMinutes: number
  breachStatus: string | null
}

interface TicketSlaPanelProps {
  entityType: SlaEntityType
  entityId: string
  /** The ticket's createdAt — used for the countdown progress bar. */
  ticketCreatedAt?: string | null
}

/**
 * Live SLA readout for a single ticket's detail page. Staff-only upstream —
 * the /api/v1/sla-instances endpoint rejects END_USER. Refreshes on a 30s poll
 * and via the shared push/notification invalidation on the 'sla-instances'
 * query-key prefix.
 */
export function TicketSlaPanel({ entityType, entityId, ticketCreatedAt }: TicketSlaPanelProps) {
  const { instance, accounts } = useMsal()
  const account = accounts[0]

  const query = useQuery<TicketSlaInstance[]>({
    queryKey: ['sla-instances', 'ticket', entityType, entityId],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/sla-instances?${PARAM[entityType]}=${entityId}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
    refetchInterval: 30_000,
  })

  const sla = query.data?.[0]

  return (
    <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-lg font-semibold">SLA</h2>
        {sla?.breachStatus && sla.breachStatus !== 'NONE' && (
          <span
            className={`rounded-full px-2 py-0.5 text-xs font-medium ${
              sla.breachStatus === 'BREACHED'
                ? 'bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-300'
                : 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/40 dark:text-yellow-300'
            }`}
          >
            {sla.breachStatus === 'BREACHED' ? 'Breached' : 'At Risk'}
          </span>
        )}
      </div>

      {query.isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : !sla ? (
        <p className="text-sm text-muted-foreground">No SLA policy applies to this ticket.</p>
      ) : (
        <dl className="space-y-3 text-sm">
          <div className="flex justify-between">
            <dt className="text-muted-foreground">Policy</dt>
            <dd className="font-medium">{sla.policyName ?? '—'}</dd>
          </div>
          <div className="flex items-center justify-between gap-4">
            <dt className="text-muted-foreground">Response</dt>
            <dd>
              <SlaCountdown
                breachStatus={sla.breachStatus}
                dueAt={sla.responseDueAt}
                metAt={sla.responseMetAt}
                pausedAt={sla.pausedAt}
                createdAt={ticketCreatedAt}
              />
            </dd>
          </div>
          <div className="flex items-center justify-between gap-4">
            <dt className="text-muted-foreground">Resolution</dt>
            <dd>
              <SlaCountdown
                breachStatus={sla.breachStatus}
                dueAt={sla.resolutionDueAt}
                metAt={sla.resolutionMetAt}
                pausedAt={sla.pausedAt}
                createdAt={ticketCreatedAt}
              />
            </dd>
          </div>
          {sla.pausedAt && (
            <div className="flex justify-between">
              <dt className="text-muted-foreground">Paused since</dt>
              <dd className="text-xs">{formatDateTime(sla.pausedAt)}</dd>
            </div>
          )}
          {sla.totalPausedMinutes > 0 && (
            <div className="flex justify-between">
              <dt className="text-muted-foreground">Total paused</dt>
              <dd className="text-xs">
                {sla.totalPausedMinutes >= 60
                  ? `${Math.floor(sla.totalPausedMinutes / 60)}h ${sla.totalPausedMinutes % 60}m`
                  : `${sla.totalPausedMinutes}m`}
              </dd>
            </div>
          )}
        </dl>
      )}
    </div>
  )
}
