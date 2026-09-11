import { useQuery } from '@tanstack/react-query'
import { useMsal } from '@azure/msal-react'
import { Link } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'

interface ReportRow {
  group?: string
  count?: number
  priority?: string
  total?: number
  breached?: number
  compliancePercent?: number
  approver?: string
  oldestDays?: number
}

function useReport(endpoint: string, key: string) {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  return useQuery<ReportRow[]>({
    queryKey: [key],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/reports${endpoint}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
    refetchInterval: 60_000,
    staleTime: 0,
  })
}

function ReportTable({ title, query, columns }: {
  title: string
  query: ReturnType<typeof useReport>
  columns: { key: keyof ReportRow; label: string }[]
}) {
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <h3 className="mb-2 text-sm font-medium text-muted-foreground">{title}</h3>
      {query.isLoading ? (
        <Loading />
      ) : query.error ? (
        <ErrorFallback error={query.error} message={`Could not load ${title.toLowerCase()}.`} onRetry={() => query.refetch()} />
      ) : !query.data || query.data.length === 0 ? (
        <p className="text-sm text-muted-foreground">No data.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-muted-foreground">
                {columns.map((c) => <th key={c.key} className="py-2 pr-4 font-medium">{c.label}</th>)}
              </tr>
            </thead>
            <tbody>
              {query.data.map((row, i) => (
                <tr key={i} className="border-b border-border last:border-0">
                  {columns.map((c) => (
                    <td key={c.key} className="py-2 pr-4">
                      {row[c.key] === undefined ? '—' : String(row[c.key])}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export function StandardReports() {
  const byCategory = useReport('/incidents-by-category', 'reports-incidents-by-category')
  const byCatalog = useReport('/requests-by-catalog', 'reports-requests-by-catalog')
  const slaByPriority = useReport('/sla-by-priority', 'reports-sla-by-priority')
  const pendingApprovals = useReport('/pending-approvals-backlog', 'reports-pending-approvals')

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold tracking-tight">Standard Reports</h1>
          <Link to="/dashboard/reports" className="inline-flex items-center gap-1 text-sm text-primary hover:underline">
            <ArrowLeft className="h-4 w-4" /> Back to reporting
          </Link>
        </div>

        <div className="grid gap-4 lg:grid-cols-2">
          <ReportTable title="Incidents by Category" query={byCategory} columns={[
            { key: 'group', label: 'Category' },
            { key: 'count', label: 'Count' },
          ]} />
          <ReportTable title="Requests by Catalog Item" query={byCatalog} columns={[
            { key: 'group', label: 'Catalog Item' },
            { key: 'count', label: 'Count' },
          ]} />
          <ReportTable title="SLA Compliance by Priority" query={slaByPriority} columns={[
            { key: 'priority', label: 'Priority' },
            { key: 'total', label: 'Total' },
            { key: 'breached', label: 'Breached' },
            { key: 'compliancePercent', label: 'Compliance %' },
          ]} />
          <ReportTable title="Pending Approvals Backlog" query={pendingApprovals} columns={[
            { key: 'approver', label: 'Approver' },
            { key: 'count', label: 'Count' },
            { key: 'oldestDays', label: 'Oldest (days)' },
          ]} />
        </div>
      </div>
    </div>
  )
}
