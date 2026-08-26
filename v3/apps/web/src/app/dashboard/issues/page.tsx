'use client'

import { useEffect, useMemo, useState } from 'react'
import { useAuth, api } from '@/lib/api'
import { cn } from '@/lib/utils'
import { Input } from '@/components/input'
import { Button } from '@/components/button'
import * as Dialog from '@radix-ui/react-dialog'
import {
  AlertTriangle,
  Bug,
  Clock,
  Inbox,
  LayoutGrid,
  List,
  MessageSquare,
  MoreHorizontal,
  Server,
  User,
  X,
} from 'lucide-react'

type IssueType = 'INCIDENT' | 'BUG' | 'REQUEST'
type IssueStatus = 'OPEN' | 'IN_PROGRESS' | 'ON_HOLD' | 'RESOLVED' | 'CLOSED' | 'CANCELLED'
type Priority = 'LOWEST' | 'LOW' | 'MEDIUM' | 'HIGH' | 'HIGHEST'

interface UserPreview {
  id: string
  name: string
  email: string
}

interface Comment {
  id: string
  content: string
  createdAt: string
  author: { id: string; name: string }
}

interface Issue {
  id: string
  ticketId: string
  type: IssueType
  title: string
  description?: string | null
  status: IssueStatus
  priority: Priority
  requesterId: string
  assigneeId?: string | null
  requester: UserPreview
  assignee?: UserPreview | null
  createdAt: string
  updatedAt: string
  slaTargetAt?: string | null
  slaBreached: boolean
  comments?: Comment[]
  cis: { ci: { id: string; ciId: string; name: string; type: string } }[]
  _count?: { comments: number }
}

const issueTypeLabel: Record<IssueType, string> = {
  INCIDENT: 'INC',
  BUG: 'BUG',
  REQUEST: 'REQ',
}

const statusLabel: Record<IssueStatus, string> = {
  OPEN: 'Open',
  IN_PROGRESS: 'In Progress',
  ON_HOLD: 'Under Review',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
  CANCELLED: 'Cancelled',
}

const priorityLabel: Record<Priority, string> = {
  LOWEST: 'P5 Lowest',
  LOW: 'P4 Low',
  MEDIUM: 'P3 Medium',
  HIGH: 'P2 High',
  HIGHEST: 'P1 Critical',
}

const priorityRank: Record<Priority, number> = {
  HIGHEST: 1,
  HIGH: 2,
  MEDIUM: 3,
  LOW: 4,
  LOWEST: 5,
}

const statusColor: Record<IssueStatus, string> = {
  OPEN: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
  IN_PROGRESS: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
  ON_HOLD: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
  RESOLVED: 'bg-violet-500/10 text-violet-400 border-violet-500/20',
  CLOSED: 'bg-slate-500/10 text-slate-400 border-slate-500/20',
  CANCELLED: 'bg-rose-500/10 text-rose-400 border-rose-500/20',
}

const priorityColor: Record<Priority, string> = {
  HIGHEST: 'bg-rose-500/10 text-rose-400 border-rose-500/20',
  HIGH: 'bg-orange-500/10 text-orange-400 border-orange-500/20',
  MEDIUM: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
  LOW: 'bg-cyan-500/10 text-cyan-400 border-cyan-500/20',
  LOWEST: 'bg-slate-500/10 text-slate-400 border-slate-500/20',
}

const typeIcon: Record<IssueType, typeof AlertTriangle> = {
  INCIDENT: AlertTriangle,
  BUG: Bug,
  REQUEST: Inbox,
}

const boardStatuses: { key: IssueStatus; label: string }[] = [
  { key: 'OPEN', label: 'Open' },
  { key: 'IN_PROGRESS', label: 'In Progress' },
  { key: 'ON_HOLD', label: 'Under Review' },
  { key: 'RESOLVED', label: 'Done' },
]

function formatSla(hours: number) {
  const h = Math.max(0, Math.floor(hours))
  const m = Math.max(0, Math.floor((hours - h) * 60))
  return `${h}h ${m}m`
}

function slaInfo(target?: string | null, created: string = '') {
  if (!target) return { remaining: '—', pct: 0, breached: false }
  const now = Date.now()
  const t = new Date(target).getTime()
  const c = new Date(created).getTime()
  const total = Math.max(1, t - c)
  const remainingMs = t - now
  const breached = remainingMs <= 0
  const elapsed = now - c
  const pct = Math.min(100, Math.max(0, (elapsed / total) * 100))
  const hours = Math.abs(remainingMs) / (1000 * 60 * 60)
  return { remaining: (breached ? '-' : '') + formatSla(hours), pct, breached }
}

function Badge({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-1 rounded border px-2 py-0.5 text-xs font-medium', className)}>
      {children}
    </span>
  )
}

export default function IssuesPage() {
  const { token, authed } = useAuth()
  const initialSearch = typeof window !== 'undefined' ? new URLSearchParams(window.location.search).get('search') || '' : ''
  const [issues, setIssues] = useState<Issue[]>([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState<'ALL' | IssueType>('ALL')
  const [search, setSearch] = useState(initialSearch)
  const [view, setView] = useState<'list' | 'board'>('list')
  const [selected, setSelected] = useState<Issue | null>(null)
  const [detail, setDetail] = useState<Issue | null>(null)

  const params = useMemo(() => {
    const p = new URLSearchParams()
    if (filter !== 'ALL') p.set('type', filter)
    if (search) p.set('search', search)
    return p.toString()
  }, [filter, search])

  const fetchIssues = async () => {
    if (!token) return
    setLoading(true)
    const res = await fetch(api(`/api/issues?${params}`), authed())
    if (res.ok) {
      const data = await res.json()
      setIssues(Array.isArray(data) ? data : data.value ?? [])
    }
    setLoading(false)
  }

  useEffect(() => {
    fetchIssues()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params, token])

  const openSheet = async (issue: Issue) => {
    setSelected(issue)
    const res = await fetch(api(`/api/issues/${issue.id}`), authed())
    if (res.ok) setDetail(await res.json())
  }

  const closeSheet = () => {
    setSelected(null)
    setDetail(null)
  }

  const filtered = useMemo(() => {
    return [...issues].sort((a, b) => priorityRank[a.priority] - priorityRank[b.priority])
  }, [issues])

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <h1 className="text-2xl font-semibold">Work Dashboard</h1>
        <div className="flex items-center gap-2">
          <div className="flex rounded-md border p-1">
            <button
              onClick={() => setView('list')}
              className={cn('rounded px-2 py-1 text-sm', view === 'list' && 'bg-accent text-accent-foreground')}
            >
              <List className="h-4 w-4" />
            </button>
            <button
              onClick={() => setView('board')}
              className={cn('rounded px-2 py-1 text-sm', view === 'board' && 'bg-accent text-accent-foreground')}
            >
              <LayoutGrid className="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-wrap gap-2">
          {(['ALL', 'INCIDENT', 'BUG', 'REQUEST'] as const).map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={cn(
                'rounded-md border px-3 py-1.5 text-xs font-medium transition-colors',
                filter === f ? 'bg-accent text-accent-foreground' : 'text-muted-foreground hover:text-foreground'
              )}
            >
              {f === 'ALL' ? 'All' : f === 'INCIDENT' ? 'Incidents' : f === 'BUG' ? 'Bugs' : 'Service Requests'}
            </button>
          ))}
        </div>
        <Input
          placeholder="Search issues..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full sm:w-64"
        />
      </div>

      {loading ? (
        <div className="py-20 text-center text-sm text-muted-foreground">Loading issues...</div>
      ) : view === 'list' ? (
        <div className="overflow-hidden rounded-lg border">
          <table className="w-full text-sm">
            <thead className="bg-muted/50 text-left text-xs uppercase text-muted-foreground">
              <tr>
                <th className="px-4 py-3 font-medium">ID</th>
                <th className="px-4 py-3 font-medium">Title</th>
                <th className="px-4 py-3 font-medium">Type</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">Priority</th>
                <th className="px-4 py-3 font-medium">Assignee</th>
                <th className="px-4 py-3 font-medium">SLA</th>
                <th className="px-4 py-3 font-medium" />
              </tr>
            </thead>
            <tbody className="divide-y">
              {filtered.map((issue) => {
                const { remaining, pct, breached } = slaInfo(issue.slaTargetAt, issue.createdAt)
                const Icon = typeIcon[issue.type]
                return (
                  <tr
                    key={issue.id}
                    onClick={() => openSheet(issue)}
                    className="cursor-pointer transition-colors hover:bg-muted/30"
                  >
                    <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{issue.ticketId}</td>
                    <td className="max-w-xs truncate px-4 py-3 font-medium">{issue.title}</td>
                    <td className="px-4 py-3">
                      <Badge className={priorityColor[issue.priority]}>
                        <Icon className="h-3 w-3" />
                        {issueTypeLabel[issue.type]}
                      </Badge>
                    </td>
                    <td className="px-4 py-3">
                      <Badge className={statusColor[issue.status]}>{statusLabel[issue.status]}</Badge>
                    </td>
                    <td className="px-4 py-3">
                      <Badge className={priorityColor[issue.priority]}>{priorityLabel[issue.priority]}</Badge>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {issue.assignee?.name || 'Unassigned'}
                    </td>
                    <td className="px-4 py-3">
                      <div className="w-24">
                        <div className="mb-1 flex items-center gap-1 text-xs text-muted-foreground">
                          <Clock className="h-3 w-3" />
                          {breached ? 'Overdue' : remaining}
                        </div>
                        <div className="h-1.5 w-full rounded-full bg-muted">
                          <div
                            className={cn('h-1.5 rounded-full', breached ? 'bg-destructive' : 'bg-primary')}
                            style={{ width: `${pct}%` }}
                          />
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <MoreHorizontal className="h-4 w-4 text-muted-foreground" />
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {boardStatuses.map((col) => (
            <div key={col.key} className="rounded-lg border p-3">
              <h3 className="mb-3 text-xs font-semibold uppercase text-muted-foreground">{col.label}</h3>
              <div className="space-y-2">
                {filtered
                  .filter((i) => (col.key === 'RESOLVED' ? i.status === 'RESOLVED' || i.status === 'CLOSED' : i.status === col.key))
                  .map((issue) => {
                    const Icon = typeIcon[issue.type]
                    return (
                      <button
                        key={issue.id}
                        onClick={() => openSheet(issue)}
                        className="w-full space-y-2 rounded-md border p-3 text-left transition-colors hover:bg-muted/30"
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-mono text-xs text-muted-foreground">{issue.ticketId}</span>
                          <Badge className={priorityColor[issue.priority]}>{priorityLabel[issue.priority]}</Badge>
                        </div>
                        <p className="text-sm font-medium">{issue.title}</p>
                        <div className="flex items-center gap-2">
                          <Icon className="h-3 w-3 text-muted-foreground" />
                          <span className="text-xs text-muted-foreground">{issue.assignee?.name || 'Unassigned'}</span>
                        </div>
                      </button>
                    )
                  })}
              </div>
            </div>
          ))}
        </div>
      )}

      <Dialog.Root open={!!selected} onOpenChange={(open) => !open && closeSheet()}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm" />
          <Dialog.Content className="fixed inset-y-0 right-0 z-50 w-full max-w-2xl border-l bg-popover shadow-2xl outline-none data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:slide-out-to-right data-[state=open]:slide-in-from-right">
            {detail ? <IssueSheet issue={detail} onClose={closeSheet} onChange={fetchIssues} /> : null}
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  )
}

function IssueSheet({ issue, onClose, onChange }: { issue: Issue; onClose: () => void; onChange: () => void }) {
  const { token, authed } = useAuth()
  const [title, setTitle] = useState(issue.title)
  const [description, setDescription] = useState(issue.description || '')
  const [status, setStatus] = useState<IssueStatus>(issue.status)
  const [assigneeId, setAssigneeId] = useState(issue.assigneeId || '')
  const [comment, setComment] = useState('')
  const [saving, setSaving] = useState(false)

  const { remaining, pct, breached } = slaInfo(issue.slaTargetAt, issue.createdAt)

  const save = async () => {
    if (!token) return
    setSaving(true)
    await fetch(api(`/api/issues/${issue.id}`), authed({
      method: 'PATCH',
      body: JSON.stringify({
        title,
        description: description || undefined,
        status,
        assigneeId: assigneeId || undefined,
      }),
    }))
    await fetch(api(`/api/issues/${issue.id}/transition`), authed({
      method: 'POST',
      body: JSON.stringify({ status }),
    }))
    setSaving(false)
    onChange()
    onClose()
  }

  const addComment = async () => {
    if (!comment || !token) return
    await fetch(api(`/api/issues/${issue.id}/comments`), authed({
      method: 'POST',
      body: JSON.stringify({ content: comment }),
    }))
    setComment('')
    onChange()
  }

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-between border-b p-4">
        <Dialog.Title className="text-sm font-mono text-muted-foreground">{issue.ticketId}</Dialog.Title>
        <Dialog.Close asChild>
          <button onClick={onClose} className="rounded-md p-1 hover:bg-muted">
            <X className="h-4 w-4" />
          </button>
        </Dialog.Close>
      </div>

      <div className="grid flex-1 grid-cols-1 divide-y overflow-y-auto md:grid-cols-[2fr_1fr] md:divide-x md:divide-y-0">
        <div className="space-y-4 p-4">
          <div>
            <label className="mb-1 block text-xs text-muted-foreground">Title</label>
            <Input value={title} onChange={(e) => setTitle(e.target.value)} className="font-medium" />
          </div>
          <div>
            <label className="mb-1 block text-xs text-muted-foreground">Description</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="min-h-[120px] w-full rounded-md border bg-background p-3 text-sm outline-none focus:ring-1 focus:ring-ring"
            />
          </div>

          <div className="flex items-center gap-2">
            <Button onClick={save} loading={saving}>Save changes</Button>
            <Button variant="ghost" onClick={onClose}>Cancel</Button>
          </div>

          <div>
            <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold">
              <MessageSquare className="h-4 w-4" /> Activity
            </h3>
            <div className="space-y-3">
              {issue.comments?.length ? (
                issue.comments.map((c) => (
                  <div key={c.id} className="rounded-md border p-3">
                    <div className="mb-1 flex items-center gap-2 text-xs text-muted-foreground">
                      <User className="h-3 w-3" />
                      {c.author.name} · {new Date(c.createdAt).toLocaleString()}
                    </div>
                    <p className="text-sm">{c.content}</p>
                  </div>
                ))
              ) : (
                <p className="text-sm text-muted-foreground">No comments yet.</p>
              )}
            </div>

            <div className="mt-4 flex gap-2">
              <Input
                placeholder="Add a comment..."
                value={comment}
                onChange={(e) => setComment(e.target.value)}
              />
              <Button onClick={addComment}>Post</Button>
            </div>
          </div>
        </div>

        <div className="space-y-5 p-4">
          <div>
            <label className="mb-1 block text-xs text-muted-foreground">Status</label>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value as IssueStatus)}
              className="w-full rounded-md border bg-background px-3 py-2 text-sm outline-none"
            >
              {Object.keys(statusLabel).map((s) => (
                <option key={s} value={s}>{statusLabel[s as IssueStatus]}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="mb-1 block text-xs text-muted-foreground">Assignee</label>
            <select
              value={assigneeId}
              onChange={(e) => setAssigneeId(e.target.value)}
              className="w-full rounded-md border bg-background px-3 py-2 text-sm outline-none"
            >
              <option value="">Unassigned</option>
              <option value={issue.requesterId}>{issue.requester?.name || 'Requester'}</option>
            </select>
          </div>

          <div>
            <label className="mb-1 block text-xs text-muted-foreground">SLA</label>
            <div className="rounded-md border p-3">
              <div className="mb-2 flex items-center justify-between text-sm">
                <span>{breached ? 'Breached' : 'Time remaining'}</span>
                <span className={breached ? 'text-destructive' : ''}>{remaining}</span>
              </div>
              <div className="h-2 w-full rounded-full bg-muted">
                <div className={cn('h-2 rounded-full', breached ? 'bg-destructive' : 'bg-primary')} style={{ width: `${pct}%` }} />
              </div>
            </div>
          </div>

          <div>
            <h4 className="mb-2 flex items-center gap-2 text-xs font-semibold text-muted-foreground">
              <Server className="h-3 w-3" /> Linked CIs
            </h4>
            {issue.cis?.length ? (
              <ul className="space-y-1 text-sm">
                {issue.cis.map((ic) => (
                  <li key={ic.ci.id} className="rounded border px-2 py-1">{ic.ci.name}</li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-muted-foreground">No linked CIs.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
