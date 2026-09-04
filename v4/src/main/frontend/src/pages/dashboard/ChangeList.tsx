import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery } from '@tanstack/react-query'
import { Calendar, Plus } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { DataTable } from '../../components/ui/DataTable'
import { StatusBadge, formatStatusLabel } from '../../components/ui/StatusBadge'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'

export interface Change {
  id: string
  number: number
  title: string
  changeType: string
  risk: string
  status: string
  plannedStart: string | null
  plannedEnd: string | null
}

const statusOptions = ['', 'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'ROLLED_BACK', 'REJECTED', 'CANCELLED']

export function ChangeList() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const [filter, setFilter] = useState('')

  const query = useQuery<Change[]>({
    queryKey: ['changes', filter],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/changes')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      return filter ? data.filter((c: Change) => c.status === filter) : data
    },
  })

  if (query.isLoading) return <Loading />
  if (query.error) return <ErrorFallback error={query.error} message="Could not load changes." onRetry={() => query.refetch()} />

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-2xl font-semibold tracking-tight">Change Requests</h1>
          <div className="flex gap-2">
            <Link
              to="/dashboard/changes/calendar"
              className="inline-flex items-center gap-2 rounded-md border border-border bg-background px-4 py-2 text-sm font-medium transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <Calendar className="h-4 w-4" />
              Calendar
            </Link>
            <Link
              to="/dashboard/changes/new"
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <Plus className="h-4 w-4" />
              New Change
            </Link>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <label htmlFor="status-filter" className="text-sm font-medium">Filter</label>
          <select
            id="status-filter"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
          >
            <option value="">All</option>
            {statusOptions.slice(1).map((s) => <option key={s} value={s}>{formatStatusLabel(s)}</option>)}
          </select>
        </div>

        <DataTable<Change>
          caption="Change requests"
          columns={[
            { key: 'number', header: '#' },
            { key: 'title', header: 'Title', render: (row) => <Link to={`/dashboard/changes/${row.id}`} className="font-medium hover:underline">{row.title}</Link> },
            { key: 'changeType', header: 'Type' },
            { key: 'risk', header: 'Risk' },
            { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
            { key: 'planned', header: 'Planned', render: (row) => `${row.plannedStart ? new Date(row.plannedStart).toLocaleDateString() : '—'} - ${row.plannedEnd ? new Date(row.plannedEnd).toLocaleDateString() : '—'}` },
          ]}
          data={query.data ?? []}
          getRowKey={(row) => row.id}
          emptyText="No change requests found."
        />
      </div>
    </div>
  )
}
