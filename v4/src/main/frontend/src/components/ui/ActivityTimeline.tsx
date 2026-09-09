import type { ReactNode } from 'react'
import { formatDateTime } from '../../lib/date'
import { formatStatusLabel } from './StatusBadge'

export interface AuditEntry {
  id: string
  actorUserId: string
  action: string
  beforeState: string | null
  afterState: string | null
  createdAt: string
}

export interface Activity {
  id: string
  title: string
  description?: string
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

function parseState(json: string | null): Record<string, unknown> {
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

export function auditDescription(entry: AuditEntry): string | undefined {
  const before = parseState(entry.beforeState)
  const after = parseState(entry.afterState)
  const parts: string[] = []
  for (const key of Object.keys(after)) {
    if (key.endsWith('Id')) continue
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
                <p className="text-xs text-muted-foreground">{formatDateTime(activity.createdAt)}</p>
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
