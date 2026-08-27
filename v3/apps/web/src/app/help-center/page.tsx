'use client'

import { useEffect, useMemo, useState } from 'react'
import { useAuth, api } from '@/lib/api'
import { Input } from '@/components/input'
import { Button } from '@/components/button'
import * as Dialog from '@radix-ui/react-dialog'
import { AlertTriangle, Book, Clock, HelpCircle, Inbox, MessageSquare, Search, ShoppingBag, User, X } from 'lucide-react'
import { cn } from '@/lib/utils'

type IssueType = 'INCIDENT' | 'BUG' | 'REQUEST'
type IssueStatus = 'OPEN' | 'IN_PROGRESS' | 'ON_HOLD' | 'RESOLVED' | 'CLOSED' | 'CANCELLED'

interface Issue {
  id: string
  ticketId: string
  type: IssueType
  title: string
  description?: string | null
  status: IssueStatus
  createdAt: string
  updatedAt: string
  comments?: { id: string; content: string; createdAt: string; author: { name: string } }[]
}

const statusLabel: Record<IssueStatus, string> = {
  OPEN: 'Submitted',
  IN_PROGRESS: 'In Review',
  ON_HOLD: 'Waiting',
  RESOLVED: 'Completed',
  CLOSED: 'Completed',
  CANCELLED: 'Cancelled',
}

const statusColor: Record<IssueStatus, string> = {
  OPEN: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
  IN_PROGRESS: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
  ON_HOLD: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
  RESOLVED: 'bg-violet-500/10 text-violet-400 border-violet-500/20',
  CLOSED: 'bg-slate-500/10 text-slate-400 border-slate-500/20',
  CANCELLED: 'bg-rose-500/10 text-rose-400 border-rose-500/20',
}

const articles = [
  { title: 'How to reset your password', content: 'Open the login page and click Forgot Password to receive a reset link.' },
  { title: 'VPN setup for remote work', content: 'Download the client from the IT portal, then enter your work email and MFA code.' },
  { title: 'Printer not responding', content: 'Check that the printer is online and try re-adding it from Windows Settings > Printers.' },
  { title: 'Requesting software licenses', content: 'Use the Request Software card and include the tool name, business justification, and number of users.' },
]

export default function HelpCenterPage() {
  const { token, authed, user } = useAuth()
  const [requests, setRequests] = useState<Issue[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<Issue | null>(null)
  const [dialog, setDialog] = useState<'incident' | 'request' | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [saving, setSaving] = useState(false)

  const fetchRequests = async () => {
    if (!token || !user) return
    setLoading(true)
    const res = await fetch(api(`/api/issues?requesterId=${user.id}`), authed())
    if (res.ok) {
      const data = await res.json()
      setRequests(Array.isArray(data) ? data : data.value ?? [])
    }
    setLoading(false)
  }

  useEffect(() => {
    fetchRequests()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, user?.id])

  const filtered = useMemo(() => {
    return requests.filter((r) =>
      r.title.toLowerCase().includes(search.toLowerCase()) ||
      r.ticketId.toLowerCase().includes(search.toLowerCase())
    )
  }, [requests, search])

  const submit = async (type: IssueType) => {
    if (!token) return
    setSaving(true)
    const res = await fetch(api('/api/issues'), authed({
      method: 'POST',
      body: JSON.stringify({
        type,
        title,
        description,
        priority: type === 'INCIDENT' ? 'HIGH' : 'MEDIUM',
      }),
    }))
    if (res.ok) {
      setDialog(null)
      setTitle('')
      setDescription('')
      fetchRequests()
    }
    setSaving(false)
  }

  return (
    <div className="mx-auto max-w-5xl space-y-8">
        <div className="space-y-2 text-center">
          <h1 className="text-3xl font-semibold">How can we help?</h1>
          <p className="text-muted-foreground">Search the knowledge base or open a request with IT.</p>
        </div>

        <div className="mx-auto max-w-2xl">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search your requests or the knowledge base..."
              className="pl-10"
            />
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <ActionCard
            icon={AlertTriangle}
            title="Report an Incident"
            description="Log a break-fix issue affecting a system or device."
            onClick={() => { setDialog('incident'); setTitle(''); setDescription('') }}
          />
          <ActionCard
            icon={ShoppingBag}
            title="Request Software / Hardware"
            description="Ask for access, equipment, or a new tool."
            onClick={() => { setDialog('request'); setTitle(''); setDescription('') }}
          />
          <ActionCard
            icon={Book}
            title="Knowledge Base"
            description="Browse troubleshooting articles and FAQs."
            onClick={() => document.getElementById('kb')?.scrollIntoView({ behavior: 'smooth' })}
          />
          <ActionCard
            icon={HelpCircle}
            title="Ask a Question"
            description="Send a message to the IT help desk."
            onClick={() => { setDialog('request'); setTitle('Question'); setDescription('') }}
          />
        </div>

        <div className="space-y-3">
          <h2 className="text-lg font-semibold">My Open Requests</h2>
          {loading ? (
            <p className="text-sm text-muted-foreground">Loading...</p>
          ) : filtered.length === 0 ? (
            <p className="text-sm text-muted-foreground">No requests found.</p>
          ) : (
            <div className="rounded-lg border">
              <table className="w-full text-sm">
                <thead className="bg-muted/50 text-left text-xs uppercase text-muted-foreground">
                  <tr>
                    <th className="px-4 py-3 font-medium">ID</th>
                    <th className="px-4 py-3 font-medium">Title</th>
                    <th className="px-4 py-3 font-medium">Status</th>
                    <th className="px-4 py-3 font-medium">Updated</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {filtered.map((r) => (
                    <tr key={r.id} onClick={() => setSelected(r)} className="cursor-pointer hover:bg-muted/30">
                      <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{r.ticketId}</td>
                      <td className="px-4 py-3">{r.title}</td>
                      <td className="px-4 py-3">
                        <span className={cn('rounded border px-2 py-0.5 text-xs font-medium', statusColor[r.status])}>
                          {statusLabel[r.status]}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">{new Date(r.updatedAt).toLocaleDateString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div id="kb" className="space-y-3">
          <h2 className="text-lg font-semibold">Knowledge Base</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            {articles.map((a) => (
              <div key={a.title} className="rounded-lg border p-4">
                <h3 className="mb-1 font-medium">{a.title}</h3>
                <p className="text-sm text-muted-foreground">{a.content}</p>
              </div>
            ))}
          </div>
        </div>

      <Dialog.Root open={!!dialog} onOpenChange={(open) => !open && setDialog(null)}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm" />
          <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-lg border bg-popover p-6 shadow-2xl outline-none">
            <Dialog.Title className="text-lg font-semibold">{dialog === 'incident' ? 'Report an Incident' : 'Submit a Request'}</Dialog.Title>
            <div className="mt-4 space-y-4">
              <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Short title" />
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Describe what is happening..."
                className="min-h-[100px] w-full rounded-md border bg-background p-3 text-sm outline-none"
              />
              <div className="flex justify-end gap-2">
                <Button variant="ghost" onClick={() => setDialog(null)}>Cancel</Button>
                <Button loading={saving} onClick={() => submit(dialog === 'incident' ? 'INCIDENT' : 'REQUEST')}>
                  Submit
                </Button>
              </div>
            </div>
            <Dialog.Close asChild>
              <button onClick={() => setDialog(null)} className="absolute right-4 top-4 rounded p-1 hover:bg-muted">
                <X className="h-4 w-4" />
              </button>
            </Dialog.Close>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>

      <Dialog.Root open={!!selected} onOpenChange={(open) => !open && setSelected(null)}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm" />
          <Dialog.Content className="fixed inset-y-0 right-0 z-50 w-full max-w-lg border-l bg-popover p-6 shadow-2xl outline-none data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:slide-out-to-right data-[state=open]:slide-in-from-right">
            {selected && <RequestPanel issue={selected} onClose={() => setSelected(null)} onChange={fetchRequests} />}
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  )
}

function ActionCard({ icon: Icon, title, description, onClick }: { icon: typeof AlertTriangle; title: string; description: string; onClick: () => void }) {
  return (
    <button onClick={onClick} className="rounded-lg border p-4 text-left transition-colors hover:bg-muted/30">
      <Icon className="mb-3 h-6 w-6 text-primary" />
      <h3 className="font-medium">{title}</h3>
      <p className="text-sm text-muted-foreground">{description}</p>
    </button>
  )
}

function RequestPanel({ issue, onClose, onChange }: { issue: Issue; onClose: () => void; onChange: () => void }) {
  const { token, authed } = useAuth()
  const [message, setMessage] = useState('')

  const send = async () => {
    if (!token || !message) return
    await fetch(api(`/api/issues/${issue.id}/comments`), authed({
      method: 'POST',
      body: JSON.stringify({ content: message }),
    }))
    setMessage('')
    onChange()
  }

  return (
    <div className="flex h-full flex-col space-y-5">
      <div className="flex items-center justify-between">
        <Dialog.Title className="text-lg font-semibold">{issue.title}</Dialog.Title>
        <Dialog.Close asChild>
          <button onClick={onClose} className="rounded p-1 hover:bg-muted">
            <X className="h-4 w-4" />
          </button>
        </Dialog.Close>
      </div>

      <div className="space-y-1">
        <p className="text-xs text-muted-foreground">{issue.ticketId}</p>
        <p className="text-sm">{issue.description}</p>
      </div>

      <div className="space-y-2">
        <h3 className="text-sm font-semibold">Status timeline</h3>
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Clock className="h-4 w-4" />
          {statusLabel[issue.status]} · updated {new Date(issue.updatedAt).toLocaleString()}
        </div>
      </div>

      <div className="flex-1 space-y-2 overflow-y-auto">
        <h3 className="text-sm font-semibold">Messages</h3>
        {issue.comments?.length ? (
          <div className="space-y-3">
            {issue.comments.map((c) => (
              <div key={c.id} className="rounded border p-3">
                <div className="mb-1 flex items-center gap-2 text-xs text-muted-foreground">
                  <User className="h-3 w-3" />
                  {c.author.name}
                </div>
                <p className="text-sm">{c.content}</p>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">No messages yet.</p>
        )}
      </div>

      <div className="flex gap-2">
        <Input value={message} onChange={(e) => setMessage(e.target.value)} placeholder="Message your IT agent..." />
        <Button onClick={send}>Send</Button>
      </div>
    </div>
  )
}
