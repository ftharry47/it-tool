import { useState, useMemo } from 'react'
import { Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { formatDate, formatWeekdayDate } from '../../lib/date'

interface CalendarItem {
  id: string
  number: number
  title: string
  locationId: string | null
  locationName: string | null
  plannedStart: string
  plannedEnd: string
}

interface Conflict {
  changeA: string
  changeB: string
}

interface CalendarResponse {
  changes: CalendarItem[]
  conflicts: Conflict[]
}

function startOfWeek(d: Date) {
  const day = d.getDay()
  const diff = d.getDate() - day
  const result = new Date(d)
  result.setDate(diff)
  result.setHours(0, 0, 0, 0)
  return result
}

function addDays(d: Date, days: number) {
  const result = new Date(d)
  result.setDate(result.getDate() + days)
  return result
}

function toIso(d: Date) {
  return d.toISOString()
}

export function ChangeCalendar() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date()))
  const weekEnd = useMemo(() => addDays(weekStart, 7), [weekStart])

  const query = useQuery<CalendarResponse>({
    queryKey: ['change-calendar', weekStart.toISOString()],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/changes/calendar?from=${encodeURIComponent(toIso(weekStart))}&to=${encodeURIComponent(toIso(weekEnd))}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const conflictIds = useMemo(() => {
    const ids = new Set<string>()
    for (const c of query.data?.conflicts ?? []) {
      ids.add(c.changeA)
      ids.add(c.changeB)
    }
    return ids
  }, [query.data?.conflicts])

  const dayLabels = Array.from({ length: 7 }, (_, i) => addDays(weekStart, i))

  if (query.isLoading) return <Loading />
  if (query.error) return <ErrorFallback error={query.error} message="Could not load change calendar." onRetry={() => query.refetch()} />

  const rangeMs = weekEnd.getTime() - weekStart.getTime()

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-2xl font-semibold tracking-tight">Change Calendar</h1>
          <Link to="/dashboard/changes" className="rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-muted">Back to List</Link>
        </div>

        <div className="flex items-center gap-2">
          <button onClick={() => setWeekStart(addDays(weekStart, -7))} className="rounded-md border border-border p-2 transition hover:bg-muted"><ChevronLeft className="h-4 w-4" /></button>
          <span className="min-w-[12rem] text-center text-sm font-medium">{formatDate(weekStart.toISOString())} - {formatDate(addDays(weekEnd, -1).toISOString())}</span>
          <button onClick={() => setWeekStart(addDays(weekStart, 7))} className="rounded-md border border-border p-2 transition hover:bg-muted"><ChevronRight className="h-4 w-4" /></button>
        </div>

        {(query.data?.conflicts?.length ?? 0) > 0 && (
          <div className="rounded-md border border-destructive/50 bg-destructive/10 p-4 text-sm text-destructive">
            <p className="font-semibold">Schedule conflicts detected</p>
            <ul className="mt-1 list-inside list-disc">
              {query.data?.conflicts.map((c, i) => {
                const a = query.data?.changes.find((x) => x.id === c.changeA)
                const b = query.data?.changes.find((x) => x.id === c.changeB)
                return <li key={i}>#{a?.number} {a?.title} overlaps with #{b?.number} {b?.title}</li>
              })}
            </ul>
          </div>
        )}

        <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
          <div className="mb-2 grid grid-cols-7 gap-2 border-b border-border pb-2 text-center text-sm text-muted-foreground">
            {dayLabels.map((d, i) => (
              <div key={i}>{formatWeekdayDate(d)}</div>
            ))}
          </div>

          <div className="relative min-h-[12rem] space-y-3">
            {query.data?.changes?.length === 0 && (
              <p className="text-center text-muted-foreground">No scheduled or in-progress changes this week.</p>
            )}
            {query.data?.changes?.map((change) => {
              const start = new Date(change.plannedStart).getTime()
              const end = new Date(change.plannedEnd).getTime()
              const left = Math.max(0, (start - weekStart.getTime()) / rangeMs * 100)
              const width = Math.max(1, (end - start) / rangeMs * 100)
              const isConflict = conflictIds.has(change.id)
              return (
                <div key={change.id} className="relative h-10 w-full rounded-md bg-muted/50">
                  <Link
                    to={`/dashboard/changes/${change.id}`}
                    className={`absolute top-1 h-8 rounded-md px-2 py-1 text-xs font-medium text-primary-foreground transition hover:opacity-90 ${
                      isConflict ? 'border-2 border-destructive bg-destructive/80' : 'bg-primary'
                    }`}
                    style={{ left: `${left}%`, width: `${width}%` }}
                    title={`#${change.number} ${change.title}`}
                  >
                    #{change.number} {change.title}
                  </Link>
                </div>
              )
            })}
          </div>
        </div>
      </div>
    </div>
  )
}
