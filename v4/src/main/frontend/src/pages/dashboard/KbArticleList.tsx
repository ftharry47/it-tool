import { Link } from 'react-router-dom'
import { useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { useQuery } from '@tanstack/react-query'
import { Plus, Search } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { DataTable } from '../../components/ui/DataTable'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'

interface KbArticleSummary {
  id: string
  number: number
  title: string
  category: string
  status: string
  version: number
}

const statusOptions = ['', 'DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'ARCHIVED']

export function KbArticleList() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const [filter, setFilter] = useState('')
  const [query, setQuery] = useState('')

  const listQuery = useQuery<KbArticleSummary[]>({
    queryKey: ['kb-articles', filter],
    queryFn: async () => {
      const url = filter ? `/api/v1/kb?status=${filter}` : '/api/v1/kb'
      const res = await fetchWithToken(instance, account!, url)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const searchQuery = useQuery<KbArticleSummary[]>({
    queryKey: ['kb-search-agent', query],
    queryFn: async () => {
      if (!query) return listQuery.data ?? []
      const res = await fetchWithToken(instance, account!, `/api/v1/kb/search?q=${encodeURIComponent(query)}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && !!query,
  })

  const isLoading = query ? searchQuery.isLoading : listQuery.isLoading
  const error = query ? searchQuery.error : listQuery.error
  const data = query ? searchQuery.data : listQuery.data

  if (isLoading) return <Loading />
  if (error) return <ErrorFallback error={error} message="Could not load articles." onRetry={() => listQuery.refetch()} />

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-2xl font-semibold tracking-tight">Knowledge Base Articles</h1>
          <Link
            to="/dashboard/kb/new"
            className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <Plus className="h-4 w-4" />
            New Article
          </Link>
        </div>

        <div className="flex flex-wrap gap-3">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search articles…"
              className="w-full rounded-md border border-input bg-background py-2 pl-9 pr-3 text-sm outline-none focus:ring-2 focus:ring-ring"
            />
          </div>
          <select
            value={filter}
            onChange={(e) => { setFilter(e.target.value); setQuery('') }}
            className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
          >
            <option value="">All</option>
            {statusOptions.slice(1).map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </div>

        <DataTable<KbArticleSummary>
          caption="Knowledge base articles"
          columns={[
            { key: 'number', header: '#' },
            { key: 'title', header: 'Title', render: (row) => <Link to={`/dashboard/kb/${row.id}`} className="font-medium hover:underline">{row.title}</Link> },
            { key: 'category', header: 'Category' },
            { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
            { key: 'version', header: 'Version' },
          ]}
          data={data ?? []}
          getRowKey={(row) => row.id}
          emptyText="No articles found."
        />
      </div>
    </div>
  )
}
