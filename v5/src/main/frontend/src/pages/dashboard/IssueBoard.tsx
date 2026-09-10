import { useMsal } from '@azure/msal-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { DndContext, MouseSensor, TouchSensor, useDraggable, useDroppable, useSensor, useSensors } from '@dnd-kit/core'
import { CSS } from '@dnd-kit/utilities'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchWithToken } from '../../api/client'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { formatStatusLabel } from '../../components/ui/StatusBadge'
import { Loading } from '../../components/ui/Loading'

interface IssueResponse {
  id: string
  key: string
  summary: string
  assigneeName: string
  storyPoints: number
  workflowStatusCategory: string
}

interface BoardColumn {
  workflowStatusId: string
  status: string
  category: string
  displayOrder: number
  issues: IssueResponse[]
}

interface WorkflowResponse {
  id: string
  name: string
  projectId: string
  statuses?: { id: string }[]
}

const CATEGORY_ORDER: Record<string, number> = {
  BACKLOG: 0,
  TODO: 1,
  IN_PROGRESS: 2,
  DONE: 3,
}

function BoardIssueCard({ issue, projectId }: { issue: IssueResponse; projectId: string }) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({ id: issue.id, data: issue })
  const style = { transform: CSS.Translate.toString(transform) }

  return (
    <Link
      to={`/dashboard/projects/${projectId}/issues/${issue.id}`}
      ref={setNodeRef}
      style={style}
      {...listeners}
      {...attributes}
      className={`block rounded-md border border-border bg-card p-3 shadow-sm transition hover:bg-muted ${isDragging ? 'opacity-50' : ''}`}
    >
      <div className="text-sm font-medium">{issue.key}</div>
      <div className="text-sm">{issue.summary}</div>
      <div className="mt-2 flex items-center justify-between text-xs text-muted-foreground">
        <span>{issue.assigneeName ?? 'Unassigned'}</span>
        {issue.storyPoints != null && <span className="rounded-full bg-muted px-2 py-0.5">{issue.storyPoints} pts</span>}
      </div>
    </Link>
  )
}

function DroppableColumn({ column, projectId }: { column: BoardColumn; projectId: string }) {
  const { isOver, setNodeRef } = useDroppable({ id: column.workflowStatusId, data: column })

  return (
    <div
      ref={setNodeRef}
      className={`flex h-full min-w-[260px] flex-1 flex-col rounded-lg border border-border bg-muted/30 p-3 transition ${isOver ? 'ring-2 ring-primary' : ''}`}
    >
      <div className="mb-2 flex items-center justify-between">
        <span className="text-sm font-semibold">{formatStatusLabel(column.status)}</span>
        <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">{column.issues.length}</span>
      </div>
      <div className="flex flex-1 flex-col gap-2 overflow-y-auto scrollbar-themed">
        {column.issues.map((issue) => (
          <BoardIssueCard key={issue.id} issue={issue} projectId={projectId} />
        ))}
      </div>
    </div>
  )
}

export function IssueBoard({ projectId, projectKey, sprintId }: { projectId: string; projectKey: string; sprintId?: string }) {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()
  const [dropError, setDropError] = useState<string | null>(null)

  const sensors = useSensors(useSensor(MouseSensor, { activationConstraint: { distance: 8 } }), useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 5 } }))

  const workflowsQuery = useQuery<WorkflowResponse[]>({
    queryKey: ['workflows'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/workflows')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const projectWorkflows = (workflowsQuery.data ?? []).filter((w) => w.projectId === projectId)
  const workflowId = (projectWorkflows.find((w) => (w.statuses?.length ?? 0) > 0) ?? projectWorkflows[0])?.id

  const boardQuery = useQuery<BoardColumn[]>({
    queryKey: ['project-board', projectKey, workflowId, sprintId ?? 'all'],
    queryFn: async () => {
      const sprintParam = sprintId ? `&sprintId=${sprintId}` : ''
      const res = await fetchWithToken(instance, account!, `/api/v1/projects/${projectKey}/board?workflowId=${workflowId}${sprintParam}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!workflowId && !!account,
  })

  const changeStatusMutation = useMutation<IssueResponse, Error, { issueId: string; workflowStatusId: string }>({
    mutationFn: async ({ issueId, workflowStatusId }) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/issues/${issueId}/status`, {
        method: 'POST',
        body: JSON.stringify({ workflowStatusId }),
      })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      setDropError(null)
      queryClient.invalidateQueries({ queryKey: ['project-board', projectKey, workflowId, sprintId ?? 'all'] })
      queryClient.invalidateQueries({ queryKey: ['project-backlog', projectId] })
    },
    onError: (err) => {
      setDropError(err.message)
    },
  })

  if (workflowsQuery.isLoading) return <Loading />
  if (workflowsQuery.error) return <ErrorFallback error={workflowsQuery.error} message="Could not load workflows." onRetry={() => workflowsQuery.refetch()} />

  if (!workflowId) {
    return <p className="text-sm text-destructive">No workflow is configured for this project. Create a workflow in the admin area.</p>
  }

  if (boardQuery.isLoading) return <Loading />
  if (boardQuery.error) return <ErrorFallback error={boardQuery.error} message="Could not load board." onRetry={() => boardQuery.refetch()} />

  const columns = [...(boardQuery.data ?? [])].sort((a, b) => {
    const catA = CATEGORY_ORDER[a.category] ?? 99
    const catB = CATEGORY_ORDER[b.category] ?? 99
    return catA - catB || a.displayOrder - b.displayOrder
  })

  if (columns.length === 0) {
    return (
      <div className="rounded-xl border border-border bg-card p-8 text-center">
        <p className="text-sm font-medium">This workflow has no statuses configured.</p>
        <p className="mt-1 text-sm text-muted-foreground">
          Add statuses to the workflow in the admin area to see board columns.
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      {dropError && (
        <div className="rounded-md border border-destructive bg-destructive/10 p-3 text-sm text-destructive">
          Illegal drop: {dropError}
        </div>
      )}
      <DndContext sensors={sensors} onDragEnd={(event) => {
        const { active, over } = event
        if (!active || !over) return
        if (active.id === over.id) return
        changeStatusMutation.mutate({ issueId: String(active.id), workflowStatusId: String(over.id) })
      }}>
        <div className="flex min-h-[500px] gap-4 overflow-x-auto scrollbar-themed rounded-xl border border-border bg-card p-4">
          {columns.map((column) => (
            <DroppableColumn key={column.workflowStatusId} column={column} projectId={projectId} />
          ))}
        </div>
      </DndContext>
    </div>
  )
}
