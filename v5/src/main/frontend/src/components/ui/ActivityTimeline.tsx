import type { ReactNode } from 'react'
import { formatDateTime } from '../../lib/date'
import { formatStatusLabel } from './StatusBadge'

export interface AuditEntry {
  id: string
  actorUserId: string
  actorName?: string | null
  actorRole?: string | null
  action: string
  beforeState: string | null
  afterState: string | null
  createdAt: string
}

export interface Activity {
  id: string
  title: string
  description?: string
  actorName?: string | null
  actorBadge?: string | null
  createdAt: string
  icon: ReactNode
}

export const AUDIT_ACTION_LABELS: Record<string, string> = {
  CREATE: 'Created',
  UPDATE: 'Updated',
  STATUS: 'Status changed',
  ASSIGN: 'Assigned',
  REASSIGN: 'Reassigned',
  UNASSIGN: 'Unassigned',
  REOPEN: 'Reopened',
  ESCALATE_PRIORITY: 'Priority escalated',
  ESCALATE_TIER: 'Escalated to next tier',
  AUTO_ESCALATE_TIER: 'Automatically escalated to next tier',
  LINK_INCIDENT: 'Incident linked',
  APPROVAL_ADDED: 'Approval added',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SUBMIT_FOR_APPROVAL: 'Submitted for approval',
}

export const AUDIT_FIELD_LABELS: Record<string, string> = {
  title: 'Title',
  description: 'Description',
  status: 'Status',
  categoryName: 'Category',
  locationName: 'Location',
  assigneeName: 'Assignee',
  priorityName: 'Priority',
  reason: 'Reason',
  rootCause: 'Root cause',
  workaround: 'Workaround',
  changeType: 'Change type',
  risk: 'Risk',
  plannedStart: 'Planned start',
  plannedEnd: 'Planned end',
  rollbackPlan: 'Rollback plan',
  postImplementationReview: 'Post-implementation review',
  requestedByName: 'Requested by',
  linkedProblemNumber: 'Linked problem',
  incidentNumber: 'Incident',
  approverName: 'Approver',
  sequenceOrder: 'Approval order',
  comment: 'Comment',
}

export function parseState(json: string | null): Record<string, unknown> {
  if (!json) return {}
  try {
    return JSON.parse(json) as Record<string, unknown>
  } catch {
    return {}
  }
}

export function auditTitle(entry: AuditEntry): string {
  return AUDIT_ACTION_LABELS[entry.action] ?? entry.action
}

function formatNamedTransition(
  action: string,
  before: Record<string, unknown>,
  after: Record<string, unknown>,
): string | undefined {
  const reason = typeof after.reason === 'string' && after.reason ? after.reason : undefined

  if (action === 'ASSIGN' || action === 'REASSIGN') {
    const beforeName = before.assigneeName == null || before.assigneeName === '' ? undefined : String(before.assigneeName)
    const afterName = after.assigneeName == null || after.assigneeName === '' ? undefined : String(after.assigneeName)
    const teamName = after.assignmentTeamName == null || after.assignmentTeamName === '' ? undefined : String(after.assignmentTeamName)
    if (!afterName) return undefined
    const destination = teamName ? `${afterName} (${teamName})` : afterName
    if (action === 'REASSIGN' && beforeName && beforeName !== afterName) {
      return `from ${beforeName} to ${destination}`
    }
    return `to ${destination}`
  }

  if (action === 'ESCALATE_TIER' || action === 'AUTO_ESCALATE_TIER') {
    const fromTeam = before.assignmentTeamName == null || before.assignmentTeamName === '' ? '—' : String(before.assignmentTeamName)
    const toTeam = after.assignmentTeamName == null || after.assignmentTeamName === '' ? '—' : String(after.assignmentTeamName)
    return reason ? `from ${fromTeam} to ${toTeam} · Reason: ${reason}` : `from ${fromTeam} to ${toTeam}`
  }

  if (action === 'ESCALATE_PRIORITY') {
    const fromPriority = before.priorityName == null || before.priorityName === '' ? '—' : String(before.priorityName)
    const toPriority = after.priorityName == null || after.priorityName === '' ? '—' : String(after.priorityName)
    return reason ? `from ${fromPriority} to ${toPriority} · Reason: ${reason}` : `from ${fromPriority} to ${toPriority}`
  }

  if (action === 'UNASSIGN') {
    const beforeName = before.assigneeName == null || before.assigneeName === '' ? undefined : String(before.assigneeName)
    return beforeName ? `from ${beforeName}` : undefined
  }

  if (action === 'REOPEN') {
    return reason ? `Reopened · Reason: ${reason}` : 'Reopened'
  }

  return undefined
}

export function auditDescription(entry: AuditEntry): string | undefined {
  const before = parseState(entry.beforeState)
  const after = parseState(entry.afterState)

  const named = formatNamedTransition(entry.action, before, after)
  if (named) return named

  const parts: string[] = []
  for (const key of Object.keys(after)) {
    if (key.endsWith('Id') || key === 'reason' || key === 'assigneeName' || key === 'assignmentTeamName' || key === 'priorityName') continue
    const label = AUDIT_FIELD_LABELS[key] ?? key
    const from = before[key] == null || before[key] === '' ? '—' : String(before[key])
    const to = after[key] == null || after[key] === '' ? '—' : String(after[key])
    if (key === 'status') {
      parts.push(`${label}: ${formatStatusLabel(from)} → ${formatStatusLabel(to)}`)
    } else if (from === to) {
      parts.push(`${label}: ${to}`)
    } else {
      parts.push(`${label}: ${from} → ${to}`)
    }
  }
  return parts.length ? parts.join(' · ') : undefined
}

interface ActivityTimelineProps {
  activities: Activity[]
  emptyText?: string
}

export function ActivityTimeline({ activities, emptyText = 'No activity yet.' }: ActivityTimelineProps) {
  return (
    <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
      <h2 className="mb-4 text-lg font-semibold">Activity Timeline</h2>
      {activities.length === 0 ? (
        <p className="text-sm text-muted-foreground">{emptyText}</p>
      ) : (
        <ol className="relative space-y-8 border-l border-border pl-6">
          {activities.map((activity) => (
            <li key={activity.id} className="relative">
              <span className="absolute -left-[2.25rem] flex h-5 w-5 items-center justify-center rounded-full bg-muted ring-4 ring-card">
                {activity.icon}
              </span>
              <div className="space-y-2">
                <p className="text-sm font-medium text-foreground">{activity.title}</p>
                {activity.description && (
                  <p className="line-clamp-2 text-xs text-muted-foreground">{activity.description}</p>
                )}
                <p className="text-xs text-muted-foreground">
                  {activity.actorName ? `by ${activity.actorName}` : 'by System'}
                  {activity.actorBadge ? ` · ${activity.actorBadge}` : ''}
                  {' · '}
                  {formatDateTime(activity.createdAt)}
                </p>
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
