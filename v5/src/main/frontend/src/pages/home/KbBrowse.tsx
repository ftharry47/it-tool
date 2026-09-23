import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Eye, Search, ThumbsUp, TrendingUp } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { formatDate } from '../../lib/date'
import { useSmartBack } from '../../lib/useSmartBack'

interface KbArticleSummary {
  id: string
  number: string
  title: string
  category: string | null
  status: string
  viewCount: number
  helpfulCount: number
  notHelpfulCount: number
  version: number
  excerpt: string | null
  updatedAt: string | null
}

interface KbSearchResult {
  id: string
  number: string
  title: string
  category: string | null
}

type SortKey = 'helpful' | 'recent' | 'viewed'

const SORT_OPTIONS: { key: SortKey; label: string }[] = [
  { key: 'helpful', label: 'Most helpful' },
  { key: 'recent', label: 'Most recent' },
  { key: 'viewed', label: 'Most viewed' },
]

function helpfulRatio(a: KbArticleSummary): number {
  const total = a.helpfulCount + a.notHelpfulCount
  return total === 0 ? 0 : a.helpfulCount / total
}

function ArticleCard({ article }: { article: KbArticleSummary }) {
  const total = article.helpfulCount + article.notHelpfulCount
  const pct = total > 0 ? Math.round((article.helpfulCount / total) * 100) : null
  return (
    <Link
      to={`/home/kb/${article.id}`}
      className="block rounded-xl border border-border bg-card p-5 shadow-sm transition hover:border-primary/50 hover:shadow-md"
    >
      <div className="flex items-start justify-between gap-3">
        <h3 className="font-medium leading-snug text-foreground">{article.title}</h3>
        {article.category && (
          <span className="shrink-0 rounded-full bg-secondary px-2.5 py-0.5 text-xs font-medium text-secondary-foreground">
            {article.category}
          </span>
        )}
      </div>
      {article.excerpt && (
        <p className="mt-2 line-clamp-2 text-sm text-muted-foreground">{article.excerpt}</p>
      )}
      <div className="mt-3 flex items-center gap-4 text-xs text-muted-foreground">
        <span className="inline-flex items-center gap-1">
          <Eye className="h-3.5 w-3.5" />
          {article.viewCount}
        </span>
        {pct !== null && (
          <span className="inline-flex items-center gap-1">
            <ThumbsUp className="h-3.5 w-3.5" />
            {pct}% helpful
          </span>
        )}
        <span className="ml-auto">Updated {formatDate(article.updatedAt)}</span>
      </div>
    </Link>
  )
}

export function KbBrowse() {
  const smartBack = useSmartBack('/home')
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState<string | null>(null)
  const [sort, setSort] = useState<SortKey>('recent')

  const listQuery = useQuery<KbArticleSummary[]>({
    queryKey: ['kb-published'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/kb?status=PUBLISHED')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  // Server-side search still runs for body matches; results are joined back to
  // the summary list so cards keep their stats/excerpt fields.
  const searchQuery = useQuery<KbSearchResult[]>({
    queryKey: ['kb-search', query],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/kb/search?q=${encodeURIComponent(query)}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && query.trim().length > 0,
  })

  const articles = listQuery.data ?? []

  const categories = useMemo(() => {
    const counts = new Map<string, number>()
    for (const a of articles) {
      if (a.category) counts.set(a.category, (counts.get(a.category) ?? 0) + 1)
    }
    return [...counts.entries()].sort((x, y) => x[0].localeCompare(y[0]))
  }, [articles])

  const popular = useMemo(
    () => [...articles].sort((a, b) => b.viewCount - a.viewCount).slice(0, 5),
    [articles]
  )

  const filtered = useMemo(() => {
    let base = articles
    if (query.trim()) {
      const ids = new Set((searchQuery.data ?? []).map((r) => r.id))
      const q = query.trim().toLowerCase()
      base = articles.filter(
        (a) =>
          ids.has(a.id) ||
          a.title.toLowerCase().includes(q) ||
          (a.category ?? '').toLowerCase().includes(q) ||
          (a.excerpt ?? '').toLowerCase().includes(q)
      )
    }
    if (category) base = base.filter((a) => a.category === category)
    const sorted = [...base]
    if (sort === 'helpful') sorted.sort((a, b) => helpfulRatio(b) - helpfulRatio(a) || b.helpfulCount - a.helpfulCount)
    else if (sort === 'viewed') sorted.sort((a, b) => b.viewCount - a.viewCount)
    else sorted.sort((a, b) => (b.updatedAt ?? '').localeCompare(a.updatedAt ?? ''))
    return sorted
  }, [articles, searchQuery.data, query, category, sort])

  if (listQuery.isLoading) return <Loading />
  if (listQuery.error) return <ErrorFallback error={listQuery.error} message="Could not load articles." onRetry={() => listQuery.refetch()} />

  const browsing = !query.trim() && !category

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-5xl space-y-6">
        <button
          onClick={smartBack}
          className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <ArrowLeft className="h-4 w-4" />
          Back
        </button>
        <h1 className="text-2xl font-semibold tracking-tight">Knowledge Base</h1>

        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
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
            value={sort}
            onChange={(e) => setSort(e.target.value as SortKey)}
            className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            aria-label="Sort articles"
          >
            {SORT_OPTIONS.map((o) => (
              <option key={o.key} value={o.key}>{o.label}</option>
            ))}
          </select>
        </div>

        {categories.length > 0 && (
          <div className="flex flex-wrap gap-2">
            <button
              onClick={() => setCategory(null)}
              className={`rounded-full px-3 py-1 text-xs font-medium transition ${
                category === null
                  ? 'bg-primary text-primary-foreground'
                  : 'border border-border bg-card text-muted-foreground hover:bg-muted'
              }`}
            >
              All ({articles.length})
            </button>
            {categories.map(([name, count]) => (
              <button
                key={name}
                onClick={() => setCategory(category === name ? null : name)}
                className={`rounded-full px-3 py-1 text-xs font-medium transition ${
                  category === name
                    ? 'bg-primary text-primary-foreground'
                    : 'border border-border bg-card text-muted-foreground hover:bg-muted'
                }`}
              >
                {name} ({count})
              </button>
            ))}
          </div>
        )}

        {browsing && popular.length > 0 && (
          <section className="space-y-3">
            <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-muted-foreground">
              <TrendingUp className="h-4 w-4" />
              Popular articles
            </h2>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {popular.slice(0, 3).map((a) => (
                <ArticleCard key={a.id} article={a} />
              ))}
            </div>
          </section>
        )}

        <section className="space-y-3">
          {!browsing && (
            <p className="text-sm text-muted-foreground">
              {filtered.length} article{filtered.length === 1 ? '' : 's'}
              {category ? ` in ${category}` : ''}
              {query.trim() ? ` matching "${query.trim()}"` : ''}
            </p>
          )}
          <div className="grid gap-3 sm:grid-cols-2">
            {filtered.map((a) => (
              <ArticleCard key={a.id} article={a} />
            ))}
          </div>
          {filtered.length === 0 && (
            <p className="py-8 text-center text-sm text-muted-foreground">
              {query || category ? 'No matching articles.' : 'No published articles.'}
            </p>
          )}
        </section>
      </div>
    </div>
  )
}
