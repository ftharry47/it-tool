import { useEffect, useState } from 'react'
import { formatDateTime } from '../../lib/date'

interface SlaCountdownProps {
  breachStatus: string | null
  resolutionDueAt?: string | null
  resolutionMetAt?: string | null
  /** Generic due/met fields (used when not an incident-resolution countdown). */
  dueAt?: string | null
  metAt?: string | null
  pausedAt?: string | null
  /** Optional: when the SLA clock started, used for the progress bar. */
  createdAt?: string | null
}

function formatDuration(ms: number): string {
  const abs = Math.abs(ms)
  const totalMinutes = Math.floor(abs / 60000)
  const days = Math.floor(totalMinutes / 1440)
  const hours = Math.floor((totalMinutes % 1440) / 60)
  const minutes = totalMinutes % 60
  if (days > 0) return `${days}d ${hours}h`
  if (hours > 0) return `${hours}h ${minutes}m`
  return `${minutes}m`
}

const STYLES = {
  onTrack: { bar: 'bg-green-500', text: 'text-green-700 dark:text-green-400', label: 'left' },
  atRisk: { bar: 'bg-yellow-500', text: 'text-yellow-700 dark:text-yellow-400', label: 'left' },
  breached: { bar: 'bg-red-500', text: 'text-red-700 dark:text-red-400', label: 'ago' },
  met: { bar: 'bg-muted-foreground/40', text: 'text-muted-foreground', label: '' },
  none: { bar: 'bg-muted', text: 'text-muted-foreground', label: '' },
} as const

export function SlaCountdown({
  breachStatus,
  resolutionDueAt,
  resolutionMetAt,
  dueAt,
  metAt,
  pausedAt,
  createdAt,
}: SlaCountdownProps) {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 30_000)
    return () => clearInterval(id)
  }, [])

  const effectiveDue = resolutionDueAt ?? dueAt
  const effectiveMet = resolutionMetAt ?? metAt

  if (effectiveMet) {
    return (
      <span className={`inline-flex items-center gap-1.5 text-xs font-medium ${STYLES.met.text}`}>
        <span className="h-2 w-2 rounded-full bg-muted-foreground/50" />
        Met
      </span>
    )
  }

  if (pausedAt) {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs font-medium text-amber-600 dark:text-amber-400">
        <span className="h-2 w-2 rounded-full bg-amber-500" />
        Paused
      </span>
    )
  }

  if (!effectiveDue) {
    return <span className="text-xs text-muted-foreground">—</span>
  }

  const due = new Date(effectiveDue).getTime()
  const remaining = due - now
  const breached = breachStatus === 'BREACHED' || remaining < 0
  const atRisk = !breached && (breachStatus === 'AT_RISK' || remaining < 30 * 60 * 1000)

  const style = breached ? STYLES.breached : atRisk ? STYLES.atRisk : STYLES.onTrack

  // Progress bar: elapsed fraction of the total window (createdAt -> due).
  let pct = 0
  if (createdAt) {
    const start = new Date(createdAt).getTime()
    const total = due - start
    if (total > 0) pct = Math.min(100, Math.max(0, ((now - start) / total) * 100))
  } else {
    pct = breached ? 100 : atRisk ? 75 : 40
  }

  const text = breached
    ? `Breached ${formatDuration(remaining)} ago`
    : `${formatDuration(remaining)} ${style.label}`

  return (
    <span className="inline-flex min-w-[110px] flex-col gap-1" title={formatDateTime(effectiveDue)}>
      <span className={`text-xs font-medium ${style.text}`}>{text}</span>
      <span className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
        <span className={`block h-full rounded-full ${style.bar}`} style={{ width: `${pct}%` }} />
      </span>
    </span>
  )
}
