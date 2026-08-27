import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery } from '@tanstack/react-query'
import { Search } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { DataTable } from '../../components/ui/DataTable'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { StatusBadge } from '../../components/ui/StatusBadge'

interface KbArticleSummary {
  id: string
  number: number
  title: string
  category: string
  status: string
}

export function KbBrowse() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const [query, setQuery] = useState('')

  const listQuery = useQuery<KbArticleSummary[]>({
    queryKey: ['kb-published'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/kb?status=PUBLISHED')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const searchQuery = useQuery<KbArticleSummary[]>({
    queryKey: ['kb-search', query],
    queryFn: async () => {
      if (!query) return listQuery.data ?? []
      const res = await fetchWithToken(instance, account!, `/api/v1/kb/search?q=${encodeURIComponent(query)}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!query,
  })

  const isLoading = query ? searchQuery.isLoading : listQuery.isLoading
  const error = query ? searchQuery.error : listQuery.error
  const data = query ? searchQuery.data : listQuery.data

  if (isLoading) return <Loading />
  if (error) return <ErrorFallback error={error} message="Could not load articles." onRetry={() => listQuery.refetch()} />

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-4xl space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">Knowledge Base</h1>

        <div className="relative">
          <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search articles…"
            className="w-full rounded-md border border-input bg-background py-2 pl-9 pr-3 text-sm outline-none focus:ring-2 focus:ring-ring"
          />
        </div>

        <DataTable<KbArticleSummary>
          caption="Knowledge base articles"
          columns={[
            { key: 'number', header: '#' },
            { key: 'title', header: 'Title', render: (row) => <Link to={`/home/kb/${row.id}`} className="font-medium hover:underline">{row.title}</Link> },
            { key: 'category', header: 'Category' },
            { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
          ]}
          data={data ?? []}
          getRowKey={(row) => row.id}
          emptyText={query ? 'No matching articles.' : 'No published articles.'}
        />
      </div>
    </div>
  )
}
