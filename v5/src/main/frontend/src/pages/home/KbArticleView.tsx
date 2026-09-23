import { useMemo, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { ArrowLeft, ChevronRight, Eye, History, ListTree, ThumbsUp, ThumbsDown } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { useDocumentTitle } from '../../components/layout/useDocumentTitle'
import { useSmartBack } from '../../lib/useSmartBack'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { formatDate } from '../../lib/date'

interface KbArticle {
  id: string
  number: string
  title: string
  category: string | null
  body: string
  status: string
  authorName: string
  viewCount: number
  helpfulCount: number
  notHelpfulCount: number
  version: number
  publishedAt: string | null
  updatedAt: string | null
  myVote: boolean | null
}

interface KbArticleSummary {
  id: string
  number: string
  title: string
  category: string | null
  viewCount: number
  updatedAt: string | null
}

interface TocItem {
  depth: 2 | 3
  text: string
  slug: string
}

function slugify(text: string): string {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, '')
    .trim()
    .replace(/\s+/g, '-')
}

function extractToc(markdown: string): TocItem[] {
  const items: TocItem[] = []
  let inFence = false
  for (const line of markdown.split('\n')) {
    if (/^```/.test(line.trim())) {
      inFence = !inFence
      continue
    }
    if (inFence) continue
    const m = /^(#{2,3})\s+(.+)$/.exec(line)
    if (m) {
      const text = m[2].replace(/[*_`~]/g, '').trim()
      items.push({ depth: m[1].length as 2 | 3, text, slug: slugify(text) })
    }
  }
  return items
}

function headingText(children: ReactNode): string {
  return (Array.isArray(children) ? children : [children])
    .map((c) => (typeof c === 'string' || typeof c === 'number' ? String(c) : ''))
    .join('')
}

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join('')
}

export function KbArticleView() {
  const { id } = useParams<{ id: string }>()
  const smartBack = useSmartBack('/home/kb')
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const { currentUser } = useAuth()
  const queryClient = useQueryClient()
  const isAgent = currentUser?.roles.some((r) => ['AGENT', 'TEAM_LEAD', 'ADMIN', 'SUPER_ADMIN'].includes(r)) ?? false

  const articleQuery = useQuery<KbArticle>({
    queryKey: ['kb-article', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/kb/${id}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!id && !!account,
  })

  useDocumentTitle(
    articleQuery.data ? `KB: ${articleQuery.data.title}` : 'Article'
  )

  const relatedQuery = useQuery<KbArticleSummary[]>({
    queryKey: ['kb-published'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/kb?status=PUBLISHED')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const feedbackMutation = useMutation<KbArticle, Error, boolean>({
    mutationFn: async (helpful) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/kb/${id}/feedback`, {
        method: 'POST',
        body: JSON.stringify({ helpful }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['kb-article', id] })
    },
  })

  const toc = useMemo(() => extractToc(articleQuery.data?.body ?? ''), [articleQuery.data?.body])

  const related = useMemo(() => {
    const article = articleQuery.data
    if (!article || !relatedQuery.data) return []
    return relatedQuery.data
      .filter((a) => a.id !== article.id && a.category === article.category)
      .sort((a, b) => (b.updatedAt ?? '').localeCompare(a.updatedAt ?? ''))
      .slice(0, 4)
  }, [articleQuery.data, relatedQuery.data])

  if (articleQuery.isLoading) return <Loading />
  if (articleQuery.error) return <ErrorFallback error={articleQuery.error} message="Could not load article." onRetry={() => articleQuery.refetch()} />
  if (!articleQuery.data) return <Loading />

  const article = articleQuery.data

  const markdownComponents = {
    h2: ({ children }: { children?: ReactNode }) => (
      <h2 id={slugify(headingText(children))} className="scroll-mt-6">{children}</h2>
    ),
    h3: ({ children }: { children?: ReactNode }) => (
      <h3 id={slugify(headingText(children))} className="scroll-mt-6">{children}</h3>
    ),
  }

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl">
        <div className="mb-4">
          <button
            onClick={smartBack}
            className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </button>
        </div>
        <nav aria-label="Breadcrumb" className="mb-4 flex items-center gap-1 text-sm text-muted-foreground">
          <Link to="/home/kb" className="hover:text-foreground hover:underline">Knowledge Base</Link>
          {article.category && (
            <>
              <ChevronRight className="h-3.5 w-3.5" />
              <Link to="/home/kb" className="hover:text-foreground hover:underline">{article.category}</Link>
            </>
          )}
          <ChevronRight className="h-3.5 w-3.5" />
          <span className="truncate text-foreground">{article.title}</span>
        </nav>

        <div className="flex gap-8">
          <div className="min-w-0 flex-1 space-y-6">
            <div className="space-y-3">
              <div className="flex items-center gap-3">
                <StatusBadge status={article.status} />
                {article.category && (
                  <span className="rounded-full bg-secondary px-2.5 py-0.5 text-xs font-medium text-secondary-foreground">
                    {article.category}
                  </span>
                )}
                <span className="text-xs text-muted-foreground">{article.number}</span>
              </div>
              <h1 className="text-3xl font-semibold tracking-tight">{article.title}</h1>
              <div className="flex items-center gap-3">
                <span className="flex h-9 w-9 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">
                  {initials(article.authorName)}
                </span>
                <div className="text-sm">
                  <p className="font-medium">{article.authorName}</p>
                  <p className="text-xs text-muted-foreground">
                    Updated {formatDate(article.updatedAt ?? article.publishedAt)} · v{article.version} ·{' '}
                    <span className="inline-flex items-center gap-1"><Eye className="h-3 w-3" />{article.viewCount} views</span>
                  </p>
                </div>
                {isAgent && (
                  <Link
                    to={`/dashboard/kb/${article.id}`}
                    className="ml-auto inline-flex items-center gap-1 rounded-md border border-border px-2.5 py-1.5 text-xs font-medium text-muted-foreground transition hover:bg-muted hover:text-foreground"
                  >
                    <History className="h-3.5 w-3.5" />
                    Version history
                  </Link>
                )}
              </div>
            </div>

            <article className="prose prose-slate max-w-none rounded-xl border border-border bg-card p-6 shadow-sm dark:prose-invert">
              <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownComponents}>
                {article.body}
              </ReactMarkdown>
            </article>

            <div className="flex items-center justify-between rounded-xl border border-border bg-card p-4 shadow-sm">
              <div className="text-sm text-muted-foreground">
                Was this helpful? {article.helpfulCount} yes · {article.notHelpfulCount} no
                {article.myVote !== null && (
                  <span className="ml-2 text-xs">(You voted {article.myVote ? 'Yes' : 'No'})</span>
                )}
              </div>
              <div className="flex gap-2">
                <button
                  onClick={() => feedbackMutation.mutate(true)}
                  disabled={feedbackMutation.isPending}
                  className={`inline-flex items-center gap-1 rounded-md px-3 py-2 text-sm font-medium transition disabled:opacity-50 ${
                    article.myVote === true
                      ? 'bg-primary text-primary-foreground'
                      : 'border border-border hover:bg-muted'
                  }`}
                >
                  <ThumbsUp className={`h-4 w-4 transition-transform duration-150 ${article.myVote === true ? 'scale-125' : 'group-active:scale-90'}`} />
                  Yes
                </button>
                <button
                  onClick={() => feedbackMutation.mutate(false)}
                  disabled={feedbackMutation.isPending}
                  className={`group inline-flex items-center gap-1 rounded-md px-3 py-2 text-sm font-medium transition disabled:opacity-50 ${
                    article.myVote === false
                      ? 'bg-primary text-primary-foreground'
                      : 'border border-border hover:bg-muted'
                  }`}
                >
                  <ThumbsDown className={`h-4 w-4 transition-transform duration-150 ${article.myVote === false ? 'scale-125' : 'group-active:scale-90'}`} />
                  No
                </button>
              </div>
            </div>

            {related.length > 0 && (
              <section className="space-y-3">
                <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
                  Related articles
                </h2>
                <div className="grid gap-3 sm:grid-cols-2">
                  {related.map((a) => (
                    <Link
                      key={a.id}
                      to={`/home/kb/${a.id}`}
                      className="block rounded-lg border border-border bg-card p-4 shadow-sm transition hover:border-primary/50 hover:shadow-md"
                    >
                      <p className="text-sm font-medium leading-snug">{a.title}</p>
                      <p className="mt-1 text-xs text-muted-foreground">
                        {a.viewCount} views · Updated {formatDate(a.updatedAt)}
                      </p>
                    </Link>
                  ))}
                </div>
              </section>
            )}
          </div>

          {toc.length > 1 && (
            <aside className="hidden w-56 shrink-0 lg:block">
              <div className="sticky top-6 space-y-2 rounded-xl border border-border bg-card p-4 shadow-sm">
                <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  <ListTree className="h-3.5 w-3.5" />
                  On this page
                </p>
                <ul className="space-y-1 text-sm">
                  {toc.map((item) => (
                    <li key={item.slug} className={item.depth === 3 ? 'pl-4' : ''}>
                      <a
                        href={`#${item.slug}`}
                        className="text-muted-foreground transition hover:text-foreground"
                      >
                        {item.text}
                      </a>
                    </li>
                  ))}
                </ul>
              </div>
            </aside>
          )}
        </div>
      </div>
    </div>
  )
}
