import { useParams } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import ReactMarkdown from 'react-markdown'
import { ThumbsUp, ThumbsDown } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { StatusBadge } from '../../components/ui/StatusBadge'

interface KbArticle {
  id: string
  number: number
  title: string
  category: string
  body: string
  status: string
  authorName: string
  viewCount: number
  helpfulCount: number
  notHelpfulCount: number
  version: number
  publishedAt: string | null
}

export function KbArticleView() {
  const { id } = useParams<{ id: string }>()
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()

  const articleQuery = useQuery<KbArticle>({
    queryKey: ['kb-article', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/kb/${id}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!id,
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

  if (articleQuery.isLoading || !articleQuery.data) return <Loading />
  if (articleQuery.error) return <ErrorFallback error={articleQuery.error} message="Could not load article." onRetry={() => articleQuery.refetch()} />

  const article = articleQuery.data

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-3xl space-y-6">
        <div className="space-y-2">
          <div className="flex items-center gap-3">
            <StatusBadge status={article.status} />
            {article.category && <span className="text-sm text-muted-foreground">{article.category}</span>}
          </div>
          <h1 className="text-3xl font-semibold tracking-tight">{article.title}</h1>
          <p className="text-sm text-muted-foreground">
            By {article.authorName} · Version {article.version} · {article.viewCount} views
          </p>
        </div>

        <article className="prose prose-slate max-w-none rounded-xl border border-border bg-card p-6 shadow-sm">
          <ReactMarkdown>{article.body}</ReactMarkdown>
        </article>

        <div className="flex items-center justify-between rounded-xl border border-border bg-card p-4 shadow-sm">
          <div className="text-sm text-muted-foreground">
            Was this helpful? {article.helpfulCount} yes · {article.notHelpfulCount} no
          </div>
          <div className="flex gap-2">
            <button
              onClick={() => feedbackMutation.mutate(true)}
              disabled={feedbackMutation.isPending}
              className="inline-flex items-center gap-1 rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
            >
              <ThumbsUp className="h-4 w-4" />
              Yes
            </button>
            <button
              onClick={() => feedbackMutation.mutate(false)}
              disabled={feedbackMutation.isPending}
              className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-2 text-sm font-medium transition hover:bg-muted disabled:opacity-50"
            >
              <ThumbsDown className="h-4 w-4" />
              No
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
