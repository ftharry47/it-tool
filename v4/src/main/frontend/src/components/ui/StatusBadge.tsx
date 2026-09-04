import { cn } from '../../lib/utils'

interface StatusBadgeProps {
  status: string
  className?: string
}

const statusStyles: Record<string, string> = {
  // Problem + Incident
  NEW: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
  OPEN: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200',
  IN_PROGRESS: 'bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200',
  INVESTIGATING: 'bg-indigo-100 text-indigo-800 dark:bg-indigo-900 dark:text-indigo-200',
  'KNOWN ERROR': 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200',
  KNOWN_ERROR: 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200',
  ON_HOLD: 'bg-slate-100 text-slate-800 dark:bg-slate-800 dark:text-slate-200',
  WAITING_ON_CUSTOMER: 'bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200',
  RESOLVED: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
  CLOSED: 'bg-muted text-muted-foreground',

  // Change
  DRAFT: 'bg-slate-100 text-slate-800 dark:bg-slate-800 dark:text-slate-200',
  PENDING: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200',
  PENDING_APPROVAL: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200',
  APPROVED: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
  SCHEDULED: 'bg-cyan-100 text-cyan-800 dark:bg-cyan-900 dark:text-cyan-200',
  COMPLETED: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
  FAILED: 'bg-destructive/20 text-destructive',
  ROLLED_BACK: 'bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200',
  REJECTED: 'bg-destructive/20 text-destructive',
  CANCELLED: 'bg-muted text-muted-foreground',

  // Service Request
  SUBMITTED: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
  IN_FULFILLMENT: 'bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200',
  FULFILLED: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',

  // Knowledge Base
  PENDING_REVIEW: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200',
  PUBLISHED: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
  ARCHIVED: 'bg-muted text-muted-foreground',

  // Workflow / Project
  BACKLOG: 'bg-slate-100 text-slate-800 dark:bg-slate-800 dark:text-slate-200',
  TODO: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
  DONE: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
}

const statusLabels: Record<string, string> = {
  // Incident / Problem
  NEW: 'New',
  OPEN: 'Open',
  IN_PROGRESS: 'In Progress',
  INVESTIGATING: 'Investigating',
  KNOWN_ERROR: 'Known Error',
  ON_HOLD: 'On Hold',
  WAITING_ON_CUSTOMER: 'Waiting on Customer',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
  REOPENED: 'Reopened',

  // Change
  DRAFT: 'Draft',
  PENDING: 'Pending',
  PENDING_APPROVAL: 'Pending Approval',
  APPROVED: 'Approved',
  SCHEDULED: 'Scheduled',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  ROLLED_BACK: 'Rolled Back',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',

  // Service Request
  SUBMITTED: 'Submitted',
  IN_FULFILLMENT: 'In Fulfillment',
  FULFILLED: 'Fulfilled',

  // Knowledge Base
  PENDING_REVIEW: 'Pending Review',
  PUBLISHED: 'Published',
  ARCHIVED: 'Archived',

  // Workflow / Project / SLA
  BACKLOG: 'Backlog',
  TODO: 'To Do',
  DONE: 'Done',
  ON_TRACK: 'On Track',
  AT_RISK: 'At Risk',
  BREACHED: 'Breached',
}

export function formatStatusLabel(status?: string | null): string {
  if (!status) return ''
  if (statusLabels[status]) return statusLabels[status]
  return status
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase())
}

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const style = statusStyles[status] ?? 'bg-secondary text-secondary-foreground'
  const label = formatStatusLabel(status)
  return (
    <span className={cn('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', style, className)}>
      {label}
    </span>
  )
}
