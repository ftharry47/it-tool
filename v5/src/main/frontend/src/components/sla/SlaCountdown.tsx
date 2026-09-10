import { useEffect, useState } from 'react'

interface SlaCountdownProps {
  dueAt: string | null | undefined
  pausedAt?: string | null | undefined
}

function formatDuration(ms: number, overdue: boolean) {
  const abs = Math.max(0, Math.abs(ms))
  const days = Math.floor(abs / (1000 * 60 * 60 * 24))
  const hours = Math.floor((abs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))
  const minutes = Math.floor((abs % (1000 * 60 * 60)) / (1000 * 60))

  const parts: string[] = []
  if (days > 0) parts.push(`${days}d`)
  if (hours > 0 || days > 0) parts.push(`${hours}h`)
  if (minutes > 0 || parts.length === 0) parts.push(`${minutes}m`)

  const prefix = overdue ? 'Overdue ' : ''
  const suffix = overdue ? '' : ' remaining'
  return `${prefix}${parts.join(' ')}${suffix}`
}

export function SlaCountdown({ dueAt, pausedAt }: SlaCountdownProps) {
  const [, setTick] = useState(0)

  useEffect(() => {
    const id = setInterval(() => setTick((t) => t + 1), 60_000)
    return () => clearInterval(id)
  }, [])

  if (pausedAt) {
    return <span className="text-amber-600 font-medium">Paused</span>
  }

  if (!dueAt) {
    return <span className="text-muted-foreground">—</span>
  }

  const due = new Date(dueAt)
  if (isNaN(due.getTime())) {
    return <span className="text-muted-foreground">Invalid</span>
  }

  const remaining = due.getTime() - Date.now()
  const overdue = remaining < 0
  const text = formatDuration(remaining, overdue)

  return (
    <span className={overdue ? 'text-destructive font-medium' : 'text-foreground'}>
      {text}
    </span>
  )
}
