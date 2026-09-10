import { useMsal } from '@azure/msal-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useDocumentTitle } from '../../components/layout/useDocumentTitle'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { formatDateTime } from '../../lib/date'

interface IssueDetailResponse {
  id: string
  key: string
  summary: string
  description: string
  assigneeName: string
  reporterName: string
  issueTypeName: string
  workflowStatusName: string
  storyPoints: number
  priority: string
  parentIssueId: string | null
  projectId: string
}

interface IssueComment {
  id: string
  body: string
  authorType: string
  createdAt: string
}

interface IssueLink {
  id: string
  toIssueKey: string
  toIssueSummary: string
  linkType: string
}

interface IssueCard {
  id: string
  key: string
  summary: string
  workflowStatusName: string
  parentIssueId: string | null
}

export function IssueDetail() {
  const { projectId, issueId } = useParams<{ projectId: string; issueId: string }>()
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()

  const [comment, setComment] = useState('')
  const [linkToId, setLinkToId] = useState('')
  const [linkType, setLinkType] = useState('RELATES_TO')

  const issueQuery = useQuery<IssueDetailResponse>({
    queryKey: ['issue', issueId],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/issues/${issueId}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!issueId,
  })

  useDocumentTitle(
    issueQuery.data ? `Issue ${issueQuery.data.key}` : 'Issue Detail'
  )

  const commentsQuery = useQuery<IssueComment[]>({
    queryKey: ['issue-comments', issueId],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/issues/${issueId}/comments`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!issueId,
  })

  const linksQuery = useQuery<IssueLink[]>({
    queryKey: ['issue-links', issueId],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/issues/${issueId}/links`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!issueId,
  })

  const projectIssuesQuery = useQuery<IssueCard[]>({
    queryKey: ['project-issues', projectId],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/issues/project/${projectId}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!projectId,
  })

  const commentMutation = useMutation<IssueComment, Error>({
    mutationFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/issues/${issueId}/comments`, {
        method: 'POST',
        body: JSON.stringify({ body: comment }),
      })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      setComment('')
      queryClient.invalidateQueries({ queryKey: ['issue-comments', issueId] })
    },
  })

  const linkMutation = useMutation<IssueLink, Error>({
    mutationFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/issues/${issueId}/links`, {
        method: 'POST',
        body: JSON.stringify({ toIssueId: linkToId, linkType }),
      })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      setLinkToId('')
      queryClient.invalidateQueries({ queryKey: ['issue-links', issueId] })
    },
  })

  if (issueQuery.isLoading || commentsQuery.isLoading || linksQuery.isLoading) return <Loading />
  if (issueQuery.error) return <ErrorFallback error={issueQuery.error} message="Could not load issue." onRetry={() => issueQuery.refetch()} />
  if (!issueQuery.data) return <Loading />

  const issue = issueQuery.data
  const subtasks = (projectIssuesQuery.data ?? []).filter((i) => i.id !== issue.id && i.parentIssueId === issue.id)

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-4xl space-y-6">
        <div>
          <Link
            to={`/dashboard/projects/${projectId}`}
            className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </Link>
        </div>
        <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <div className="flex items-start justify-between">
            <div>
              <div className="text-sm text-muted-foreground">{issue.key}</div>
              <h1 className="text-2xl font-semibold tracking-tight">{issue.summary}</h1>
            </div>
            <span className="rounded-md bg-muted px-3 py-1 text-sm font-medium">{issue.workflowStatusName}</span>
          </div>
          <div className="mt-4 grid grid-cols-2 gap-4 text-sm md:grid-cols-4">
            <div><span className="text-muted-foreground">Type:</span> {issue.issueTypeName ?? '—'}</div>
            <div><span className="text-muted-foreground">Priority:</span> {issue.priority}</div>
            <div><span className="text-muted-foreground">Assignee:</span> {issue.assigneeName ?? 'Unassigned'}</div>
            <div><span className="text-muted-foreground">Reporter:</span> {issue.reporterName}</div>
            <div className="col-span-2"><span className="text-muted-foreground">Points:</span> {issue.storyPoints ?? '—'}</div>
          </div>
          {issue.description && (
            <div className="mt-6 whitespace-pre-wrap rounded-md bg-muted p-4 text-sm">
              {issue.description}
            </div>
          )}
        </div>

        <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
          <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
            <h2 className="mb-4 text-lg font-semibold">Comments</h2>
            <div className="mb-4 space-y-3">
              {commentsQuery.data?.map((c) => (
                <div key={c.id} className="rounded-md border border-border p-3 text-sm">
                  <p>{c.body}</p>
                  <p className="mt-1 text-xs text-muted-foreground">{c.authorType} · {formatDateTime(c.createdAt)}</p>
                </div>
              ))}
            </div>
            <div className="flex gap-2">
              <input
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                placeholder="Add a comment"
                className="flex-1 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              />
              <button
                onClick={() => commentMutation.mutate()}
                disabled={!comment || commentMutation.isPending}
                className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
              >
                Add
              </button>
            </div>
            {commentMutation.error && <p className="mt-2 text-sm text-destructive">{commentMutation.error.message}</p>}
          </div>

          <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
            <h2 className="mb-4 text-lg font-semibold">Links</h2>
            <div className="mb-4 space-y-2">
              {linksQuery.data?.map((l) => (
                <div key={l.id} className="text-sm">
                  <span className="font-medium">{l.linkType}</span> <span className="text-muted-foreground">{l.toIssueKey}</span> — {l.toIssueSummary}
                </div>
              ))}
            </div>
            <div className="space-y-2">
              <select value={linkType} onChange={(e) => setLinkType(e.target.value)} className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring">
                <option value="BLOCKS">Blocks</option>
                <option value="BLOCKED_BY">Blocked by</option>
                <option value="RELATES_TO">Relates to</option>
                <option value="DUPLICATES">Duplicates</option>
              </select>
              <input
                value={linkToId}
                onChange={(e) => setLinkToId(e.target.value)}
                placeholder="Linked issue ID"
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              />
              <button
                onClick={() => linkMutation.mutate()}
                disabled={!linkToId || linkMutation.isPending}
                className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
              >
                Add Link
              </button>
              {linkMutation.error && <p className="text-sm text-destructive">{linkMutation.error.message}</p>}
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold">Subtasks</h2>
          {subtasks.length === 0 && <p className="text-sm text-muted-foreground">No subtasks.</p>}
          <div className="space-y-2">
            {subtasks.map((s) => (
              <div key={s.id} className="rounded-md border border-border p-3 text-sm">
                <span className="font-medium">{s.key}</span> — {s.summary} <span className="text-muted-foreground">({s.workflowStatusName})</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
