import { useMsal } from '@azure/msal-react'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useDocumentTitle } from '../../components/layout/useDocumentTitle'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { Loading } from '../../components/ui/Loading'
import { IssueBoard } from './IssueBoard'
import { SprintPanel } from './SprintPanel'

interface ProjectResponse {
  id: string
  key: string
  name: string
  description: string
  leadName: string
  status: string
}

interface IssueResponse {
  id: string
  key: string
  summary: string
  workflowStatusName: string
  workflowStatusCategory: string
  assigneeName: string
  storyPoints: number
}

export function ProjectDetail() {
  const { id } = useParams<{ id: string }>()
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const [tab, setTab] = useState<'board' | 'backlog' | 'sprints'>('board')

  const projectQuery = useQuery<ProjectResponse>({
    queryKey: ['project', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/projects/${id}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!id,
  })

  useDocumentTitle(
    projectQuery.data ? `Project ${projectQuery.data.key}` : 'Project Detail'
  )

  const backlogQuery = useQuery<IssueResponse[]>({
    queryKey: ['project-backlog', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/issues/project/${id}/backlog`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!id && tab === 'backlog',
  })

  if (projectQuery.isLoading) return <Loading />
  if (projectQuery.error) return <ErrorFallback error={projectQuery.error} message="Could not load project." onRetry={() => projectQuery.refetch()} />
  if (!projectQuery.data) return <Loading />
  const project = projectQuery.data

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-4">
        <div>
          <Link
            to="/dashboard/projects"
            className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </Link>
        </div>
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">{project.key} — {project.name}</h1>
            <p className="text-sm text-muted-foreground">Lead: {project.leadName ?? 'Unassigned'} · {project.status}</p>
          </div>
        </div>

        <div className="flex gap-2 border-b border-border pb-1">
          {(['board', 'backlog', 'sprints'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`rounded-t-md px-4 py-2 text-sm font-medium capitalize transition ${tab === t ? 'border-b-2 border-primary text-primary' : 'text-muted-foreground hover:text-foreground'}`}
            >
              {t}
            </button>
          ))}
        </div>

        {tab === 'board' ? (
          <IssueBoard projectId={project.id} projectKey={project.key} />
        ) : tab === 'backlog' ? (
          <div className="space-y-2">
            {backlogQuery.isLoading && <Loading />}
            {backlogQuery.error && <ErrorFallback error={backlogQuery.error} message="Could not load backlog." onRetry={() => backlogQuery.refetch()} />}
            {(backlogQuery.data ?? []).length === 0 && <p className="text-sm text-muted-foreground">Backlog empty.</p>}
            {(backlogQuery.data ?? []).map((issue) => (
              <Link
                key={issue.id}
                to={`/dashboard/projects/${id}/issues/${issue.id}`}
                className="block rounded-md border border-border p-3 transition hover:bg-muted"
              >
                <div className="flex items-center gap-2 text-sm font-medium">
                  <span className="text-muted-foreground">{issue.key}</span>
                  <span>{issue.summary}</span>
                  <span className="ml-auto rounded-full bg-muted px-2 py-0.5 text-xs">{issue.workflowStatusName}</span>
                </div>
              </Link>
            ))}
          </div>
        ) : (
          <SprintPanel projectId={project.id} />
        )}
      </div>
    </div>
  )
}
