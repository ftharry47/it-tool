'use client'

import { useState, FormEvent } from 'react'
import Link from 'next/link'
import { useAuth, api } from '@/lib/api'
import { Button } from '@/components/button'
import { Input } from '@/components/input'
import { Ticket, CheckCircle2, ArrowLeft } from 'lucide-react'

type IssueType = 'INCIDENT' | 'BUG' | 'REQUEST'
type Priority = 'LOWEST' | 'LOW' | 'MEDIUM' | 'HIGH' | 'HIGHEST'

export default function DashboardSubmitPage() {
  const { loaded, token, authed, user } = useAuth()
  const initialType: IssueType =
    (typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('type') as IssueType) ||
    'REQUEST'
  const [type, setType] = useState<IssueType>(initialType)
  const [priority, setPriority] = useState<Priority>('MEDIUM')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [saving, setSaving] = useState(false)
  const [created, setCreated] = useState<{ ticketId: string; title: string } | null>(null)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!token || !title) return
    setSaving(true)
    const res = await fetch(api('/api/issues'), authed({
      method: 'POST',
      body: JSON.stringify({
        type,
        title,
        description: description || undefined,
        priority,
      }),
    }))
    if (res.ok) {
      const data = await res.json()
      setCreated({ ticketId: data.ticketId, title: data.title })
      setTitle('')
      setDescription('')
      setType('REQUEST')
      setPriority('MEDIUM')
    }
    setSaving(false)
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-semibold flex items-center gap-2">
          <Ticket className="h-6 w-6" /> New Ticket
        </h1>
        <Link href="/dashboard/issues" className="text-sm text-muted-foreground hover:text-foreground flex items-center gap-1">
          <ArrowLeft className="h-4 w-4" /> Back to issues
        </Link>
      </div>

      <div className="rounded-lg border p-6">
        {!loaded ? (
          <p className="text-sm text-muted-foreground">Loading...</p>
        ) : !user ? (
          <div className="space-y-3">
            <p className="text-sm text-muted-foreground">Sign in to submit a ticket.</p>
            <Link href="/login?returnTo=/dashboard/submit" className="inline-block rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90">
              Sign in
            </Link>
          </div>
        ) : (
          <form onSubmit={submit} className="space-y-4">
            {created && (
              <div className="rounded-md bg-emerald-500/10 p-3 text-sm text-emerald-400">
                <div className="flex items-center gap-2 font-medium">
                  <CheckCircle2 className="h-4 w-4" />
                  Ticket created
                </div>
                <p className="mt-1 text-xs text-emerald-300/80">
                  {created.ticketId}: {created.title}
                </p>
              </div>
            )}

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1">
                <label className="text-xs text-muted-foreground">Type</label>
                <select
                  value={type}
                  onChange={(e) => setType(e.target.value as IssueType)}
                  className="w-full rounded-md border bg-background px-3 py-2 text-sm outline-none"
                >
                  <option value="REQUEST">Service Request</option>
                  <option value="INCIDENT">Incident</option>
                  <option value="BUG">Bug</option>
                </select>
              </div>
              <div className="space-y-1">
                <label className="text-xs text-muted-foreground">Priority</label>
                <select
                  value={priority}
                  onChange={(e) => setPriority(e.target.value as Priority)}
                  className="w-full rounded-md border bg-background px-3 py-2 text-sm outline-none"
                >
                  <option value="LOWEST">Lowest</option>
                  <option value="LOW">Low</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option>
                  <option value="HIGHEST">Highest</option>
                </select>
              </div>
            </div>

            <div className="space-y-1">
              <label className="text-xs text-muted-foreground">Title</label>
              <Input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Short summary of your issue"
              />
            </div>

            <div className="space-y-1">
              <label className="text-xs text-muted-foreground">Description</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Describe the problem or request..."
                className="min-h-[120px] w-full rounded-md border bg-background p-3 text-sm outline-none"
              />
            </div>

            <Button type="submit" loading={saving} className="w-full sm:w-auto">
              Submit ticket
            </Button>
          </form>
        )}
      </div>
    </div>
  )
}
