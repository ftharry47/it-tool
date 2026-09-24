import { useEffect, useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Loader2, Lock } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { formatDateTime } from '../../lib/date'
import { renderCommentBody } from '../../lib/commentBody'
import { Loading } from './Loading'
import { MentionTextarea } from './MentionTextarea'

export interface CommentItem {
  id: string
  body: string
  isPublic: boolean
  author: string | null
  authorType: string
  createdAt: string
}

interface CommentThreadProps {
  /** e.g. `/api/v1/service-requests/${id}` — comments live at `${baseUrl}/comments` */
  baseUrl: string
  queryKey: string
  /** AGENT+ viewers see internal comments and may post them. */
  canPostInternal: boolean
  /** Whether the current user may post at all (e.g. requester or staff). */
  canPost?: boolean
}

export function CommentThread({ baseUrl, queryKey, canPostInternal, canPost = true }: CommentThreadProps) {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()
  const [body, setBody] = useState('')
  const [isInternal, setIsInternal] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [justPostedId, setJustPostedId] = useState<string | null>(null)

  // Clear the new-comment flash shortly after it appears.
  useEffect(() => {
    if (justPostedId == null) return
    const t = setTimeout(() => setJustPostedId(null), 1800)
    return () => clearTimeout(t)
  }, [justPostedId])

  const commentsQuery = useQuery<CommentItem[]>({
    queryKey: [queryKey],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `${baseUrl}/comments`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const postMutation = useMutation<CommentItem, Error, { body: string; isPublic: boolean }>({
    mutationFn: async (payload) => {
      const res = await fetchWithToken(instance, account!, `${baseUrl}/comments`, {
        method: 'POST',
        body: JSON.stringify(payload),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: (created) => {
      setBody('')
      setIsInternal(false)
      setError(null)
      setJustPostedId(created.id)
      queryClient.invalidateQueries({ queryKey: [queryKey] })
    },
    onError: (e) => setError(`Failed to post comment: ${e.message}`),
  })

  // Server-side filtering already removes internal comments for non-staff;
  // this is defense-in-depth in case a future caller changes.
  const comments = (commentsQuery.data ?? []).filter((c) => canPostInternal || c.isPublic)

  return (
    <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
      <h2 className="mb-4 text-lg font-semibold">Comments</h2>

      {commentsQuery.isLoading ? (
        <Loading compact />
      ) : comments.length === 0 ? (
        <p className="text-sm text-muted-foreground">No comments yet.</p>
      ) : (
        <ul className="space-y-4">
          {comments.map((c) => (
            <li
              key={c.id}
              className={`rounded-md border border-border/60 bg-muted/30 p-3${
                c.id === justPostedId ? ' animate-fade-slide-up animate-comment-flash' : ''
              }`}
            >
              <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
                <span className="font-medium text-foreground">
                  {c.author ?? 'Unknown'}
                  {c.authorType === 'AUTOMATION' && ' (automation)'}
                </span>
                <span className="flex items-center gap-2">
                  {!c.isPublic && (
                    <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-medium text-amber-800 dark:bg-amber-900/40 dark:text-amber-300">
                      <Lock className="h-3 w-3" /> Work Notes
                    </span>
                  )}
                  {formatDateTime(c.createdAt)}
                </span>
              </div>
              <p className="mt-1 whitespace-pre-wrap text-sm">{renderCommentBody(c.body)}</p>
            </li>
          ))}
        </ul>
      )}

      {canPost && (
        <form
          className="mt-4 space-y-2"
          onSubmit={(e) => {
            e.preventDefault()
            if (!body.trim()) return
            postMutation.mutate({ body: body.trim(), isPublic: !(canPostInternal && isInternal) })
          }}
        >
          <MentionTextarea
            value={body}
            onChange={setBody}
            rows={3}
            placeholder="Add a comment…"
            className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
          />
          <div className="flex items-center justify-between">
            {canPostInternal ? (
              <label className="flex items-center gap-2 text-xs text-muted-foreground">
                <input
                  type="checkbox"
                  checked={isInternal}
                  onChange={(e) => setIsInternal(e.target.checked)}
                  className="rounded border-input"
                />
                Work note (hidden from requester)
              </label>
            ) : (
              <span />
            )}
            <button
              type="submit"
              disabled={!body.trim() || postMutation.isPending}
              className="inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
            >
              {postMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {postMutation.isPending ? 'Posting…' : 'Post'}
            </button>
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
        </form>
      )}
    </section>
  )
}
