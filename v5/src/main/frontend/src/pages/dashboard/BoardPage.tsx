import { useEffect, useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { useQuery } from '@tanstack/react-query'
import { fetchWithToken } from '../../api/client'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { Loading } from '../../components/ui/Loading'
import { IssueBoard } from './IssueBoard'

interface ProjectResponse {
  id: string
  key: string
  name: string
}

interface SprintResponse {
  id: string
  projectId: string
  name: string
  status: 'PLANNING' | 'ACTIVE' | 'COMPLETED'
}

export function BoardPage() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const [selectedProjectId, setSelectedProjectId] = useState('')
  const [selectedSprintId, setSelectedSprintId] = useState('')

  const projectsQuery = useQuery<ProjectResponse[]>({
    queryKey: ['projects'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/projects')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  useEffect(() => {
    const projects = projectsQuery.data
    if (projects && projects.length > 0 && !selectedProjectId) {
      setSelectedProjectId(projects[0].id)
    }
  }, [projectsQuery.data, selectedProjectId])

  const selectedProject = projectsQuery.data?.find((p) => p.id === selectedProjectId)

  const sprintsQuery = useQuery<SprintResponse[]>({
    queryKey: ['project-sprints', selectedProjectId],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/projects/${selectedProjectId}/sprints`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!selectedProjectId && !!account,
  })

  useEffect(() => {
    setSelectedSprintId('')
  }, [selectedProjectId])

  if (projectsQuery.isLoading) return <Loading />
  if (projectsQuery.error) return <ErrorFallback error={projectsQuery.error} message="Could not load projects." onRetry={() => projectsQuery.refetch()} />

  const projects = projectsQuery.data ?? []

  if (projects.length === 0) {
    return (
      <div className="min-h-full bg-background p-6 text-foreground">
        <div className="mx-auto max-w-6xl">
          <h1 className="text-2xl font-semibold tracking-tight">Board</h1>
          <p className="mt-4 text-sm text-muted-foreground">No projects available. Create a project first.</p>
        </div>
      </div>
    )
  }

  const activeSprints = (sprintsQuery.data ?? []).filter((s) => s.status === 'ACTIVE')

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-4">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Board</h1>
            <p className="text-sm text-muted-foreground">Select a project and optional sprint to view the board.</p>
          </div>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <div className="space-y-1">
              <label htmlFor="board-project" className="text-xs font-medium text-muted-foreground">Project</label>
              <select
                id="board-project"
                value={selectedProjectId}
                onChange={(e) => setSelectedProjectId(e.target.value)}
                className="w-56 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              >
                {projects.map((p) => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <label htmlFor="board-sprint" className="text-xs font-medium text-muted-foreground">Sprint</label>
              <select
                id="board-sprint"
                value={selectedSprintId}
                onChange={(e) => setSelectedSprintId(e.target.value)}
                className="w-56 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              >
                <option value="">All issues</option>
                {activeSprints.map((s) => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </select>
            </div>
          </div>
        </div>

        {selectedProject ? (
          <IssueBoard
            projectId={selectedProject.id}
            projectKey={selectedProject.key}
            sprintId={selectedSprintId || undefined}
          />
        ) : (
          <p className="text-sm text-muted-foreground">Select a project to view the board.</p>
        )}
      </div>
    </div>
  )
}
