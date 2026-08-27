import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useMsal } from '@azure/msal-react'
import { fetchWithToken } from '../../api/client'
import { DataTable } from '../../components/ui/DataTable'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'

export interface Problem {
  id: string
  number: number
  title: string
  status: string
  rootCause: string | null
}

export function ProblemList() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]

  const query = useQuery<Problem[]>({
    queryKey: ['problems'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/problems')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  if (query.isLoading) return <Loading />
  if (query.error) return <ErrorFallback error={query.error} message="Could not load problems." onRetry={() => query.refetch()} />

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">Problems</h1>

        <DataTable<Problem>
          caption="List of problems"
          columns={[
            { key: 'number', header: 'Number' },
            { key: 'title', header: 'Title', render: (row) => <Link to={`/dashboard/problems/${row.id}`} className="font-medium hover:underline">{row.title}</Link> },
            { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
            { key: 'rootCause', header: 'Root Cause', render: (row) => row.rootCause ?? '—' },
          ]}
          data={query.data ?? []}
          getRowKey={(row) => row.id}
          emptyText="No problems found."
        />
      </div>
    </div>
  )
}
