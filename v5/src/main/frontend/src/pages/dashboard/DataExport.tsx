import { useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { ArrowLeft, Download } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { useSmartBack } from '../../lib/useSmartBack'
import { DateInput } from '../../components/ui/DateInput'
import { ErrorFallback } from '../../components/ui/ErrorFallback'

const ENTITIES: { value: string; label: string; statuses: string[] }[] = [
  {
    value: 'incident',
    label: 'Incidents',
    statuses: ['NEW', 'IN_PROGRESS', 'ON_HOLD', 'WAITING_ON_CUSTOMER', 'RESOLVED', 'CLOSED', 'REOPENED'],
  },
  {
    value: 'service_request',
    label: 'Service Requests',
    statuses: ['SUBMITTED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'REJECTED_NEEDS_REVIEW', 'IN_FULFILLMENT', 'ON_HOLD', 'FULFILLED', 'CANCELLED'],
  },
  {
    value: 'problem',
    label: 'Problems',
    statuses: ['NEW', 'INVESTIGATING', 'KNOWN_ERROR', 'RESOLVED', 'CLOSED'],
  },
  {
    value: 'change',
    label: 'Changes',
    statuses: ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'ROLLED_BACK', 'CANCELLED', 'CLOSED'],
  },
  {
    value: 'sla_instance',
    label: 'SLA Instances',
    statuses: ['ON_TRACK', 'AT_RISK', 'BREACHED'],
  },
]

function toIso(date: string, endOfDay: boolean) {
  if (!date) return ''
  return new Date(`${date}T${endOfDay ? '23:59:59.999' : '00:00:00.000'}Z`).toISOString()
}

/**
 * Row-level export for admins — every field with resolved display names,
 * filtered by entity, date range, and status. CSV or XLSX.
 */
export function DataExport() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const { currentUser } = useAuth()
  const isAdmin = (currentUser?.roles ?? []).some((r) => r === 'ADMIN' || r === 'SUPER_ADMIN')
  const smartBack = useSmartBack('/dashboard/reports')

  const [entity, setEntity] = useState('incident')
  const [status, setStatus] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const entityDef = ENTITIES.find((e) => e.value === entity)!

  const download = async (format: 'csv' | 'xlsx') => {
    setPending(true)
    setError(null)
    try {
      const params = new URLSearchParams({ entity, format })
      if (from) params.set('from', toIso(from, false))
      if (to) params.set('to', toIso(to, true))
      if (status) params.set('status', status)
      const res = await fetchWithToken(instance, account!, `/api/v1/reports/export?${params}`)
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${entity}-export.${format}`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Export failed')
    } finally {
      setPending(false)
    }
  }

  if (!isAdmin) {
    return (
      <div className="min-h-full bg-background p-6 text-foreground">
        <ErrorFallback error={null} message="Data export is available to administrators only." onRetry={() => {}} />
      </div>
    )
  }

  const selectCls = 'w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring'

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-6">
        <div>
          <div className="flex items-center gap-3">
            <button
                        onClick={smartBack}
                        className="mb-2 inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      >
                        <ArrowLeft className="h-4 w-4" />
                        Back
                      </button>
            <div>
              <h1 className="text-2xl font-semibold tracking-tight">Data Export</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                          Pull full row-level data — every ticket or SLA instance with resolved names — filtered by date range and status. Dates render in US Eastern time.
                        </p>
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-border bg-card p-6 shadow-sm space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <label className="text-sm font-medium">Data set</label>
              <select value={entity} onChange={(e) => { setEntity(e.target.value); setStatus('') }} className={selectCls}>
                {ENTITIES.map((e) => <option key={e.value} value={e.value}>{e.label}</option>)}
              </select>
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">{entity === 'sla_instance' ? 'Breach status' : 'Status'}</label>
              <select value={status} onChange={(e) => setStatus(e.target.value)} className={selectCls}>
                <option value="">All</option>
                {entityDef.statuses.map((s) => <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>)}
              </select>
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">Created from</label>
              <DateInput value={from} onChange={(e) => setFrom(e.target.value)} />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">Created to</label>
              <DateInput value={to} onChange={(e) => setTo(e.target.value)} />
            </div>
          </div>

          <div className="flex flex-wrap gap-2 pt-2">
            <button
              onClick={() => download('csv')}
              disabled={pending}
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
            >
              <Download className="h-4 w-4" />
              Export CSV
            </button>
            <button
              onClick={() => download('xlsx')}
              disabled={pending}
              className="inline-flex items-center gap-2 rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-muted disabled:opacity-50"
            >
              <Download className="h-4 w-4" />
              Export Excel
            </button>
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>
      </div>
    </div>
  )
}
