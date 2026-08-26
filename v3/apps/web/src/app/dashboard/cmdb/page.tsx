'use client'

import { useEffect, useState } from 'react'
import { useAuth, api } from '@/lib/api'
import { Input } from '@/components/input'
import * as Dialog from '@radix-ui/react-dialog'
import { AlertTriangle, CheckCircle2, Network, Search, Server, X } from 'lucide-react'
import { cn } from '@/lib/utils'

interface CI {
  id: string
  ciId: string
  name: string
  type: string
  category?: string | null
  status: string
  owner?: { name: string } | null
  toRels: { to: { id: string; ciId: string; name: string } }[]
  issues: { issue: { id: string; ticketId: string; title: string; status: string } }[]
}

const opStatus: Record<string, string> = {
  operational: 'Operational',
  degraded: 'Degraded',
  outage: 'Outage',
  maintenance: 'Maintenance',
}

const statusColor: Record<string, string> = {
  operational: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
  degraded: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
  outage: 'bg-rose-500/10 text-rose-400 border-rose-500/20',
  maintenance: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
}

export default function CmdbPage() {
  const { token, authed } = useAuth()
  const [cis, setCis] = useState<CI[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<CI | null>(null)

  const fetchCis = async () => {
    if (!token) return
    setLoading(true)
    const res = await fetch(api('/api/cmdb'), authed())
    if (res.ok) setCis(await res.json())
    setLoading(false)
  }

  useEffect(() => {
    fetchCis()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token])

  const filtered = cis.filter((c) =>
    c.name.toLowerCase().includes(search.toLowerCase()) ||
    c.ciId.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <h1 className="text-2xl font-semibold">Configuration Items</h1>
        <Input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search assets..."
          className="w-full sm:w-72"
        />
      </div>

      {loading ? (
        <p className="text-sm text-muted-foreground">Loading assets...</p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((ci) => {
            const activeIncidents = ci.issues.filter((i) => i.issue.status === 'OPEN' || i.issue.status === 'IN_PROGRESS')
            return (
              <button
                key={ci.id}
                onClick={() => setSelected(ci)}
                className="rounded-lg border p-4 text-left transition-colors hover:bg-muted/30"
              >
                <div className="mb-2 flex items-center justify-between">
                  <span className="font-mono text-xs text-muted-foreground">{ci.ciId}</span>
                  <span className={cn('rounded border px-2 py-0.5 text-xs font-medium', statusColor[ci.status] || statusColor.operational)}>
                    {opStatus[ci.status] || ci.status}
                  </span>
                </div>
                <h3 className="font-medium">{ci.name}</h3>
                <p className="text-sm text-muted-foreground">{ci.type}{ci.category ? ` · ${ci.category}` : ''}</p>
                <div className="mt-3 flex items-center gap-3 text-sm text-muted-foreground">
                  <span className="flex items-center gap-1"><Network className="h-3 w-3" /> {ci.toRels.length} deps</span>
                  {activeIncidents.length > 0 && (
                    <span className="flex items-center gap-1 text-destructive"><AlertTriangle className="h-3 w-3" /> {activeIncidents.length} active</span>
                  )}
                </div>
              </button>
            )
          })}
        </div>
      )}

      <Dialog.Root open={!!selected} onOpenChange={(open) => !open && setSelected(null)}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm" />
          <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-full max-w-2xl -translate-x-1/2 -translate-y-1/2 rounded-lg border bg-popover p-6 shadow-2xl outline-none">
            {selected && (
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-mono text-xs text-muted-foreground">{selected.ciId}</p>
                    <h2 className="text-xl font-semibold">{selected.name}</h2>
                  </div>
                  <Dialog.Close asChild>
                    <button onClick={() => setSelected(null)} className="rounded p-1 hover:bg-muted">
                      <X className="h-4 w-4" />
                    </button>
                  </Dialog.Close>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="rounded-lg border p-4">
                    <h3 className="mb-2 text-sm font-semibold">Details</h3>
                    <dl className="space-y-1 text-sm">
                      <div className="flex justify-between"><dt className="text-muted-foreground">Type</dt><dd>{selected.type}</dd></div>
                      <div className="flex justify-between"><dt className="text-muted-foreground">Category</dt><dd>{selected.category || '—'}</dd></div>
                      <div className="flex justify-between"><dt className="text-muted-foreground">Owner</dt><dd>{selected.owner?.name || 'Unassigned'}</dd></div>
                      <div className="flex justify-between"><dt className="text-muted-foreground">Status</dt><dd>{opStatus[selected.status] || selected.status}</dd></div>
                    </dl>
                  </div>

                  <div className="rounded-lg border p-4">
                    <h3 className="mb-2 text-sm font-semibold flex items-center gap-2"><Network className="h-4 w-4" /> Depends on</h3>
                    {selected.toRels.length ? (
                      <ul className="space-y-1 text-sm">
                        {selected.toRels.map((rel) => (
                          <li key={rel.to.id} className="flex items-center gap-2">
                            <CheckCircle2 className="h-3 w-3 text-emerald-400" />
                            {rel.to.name} <span className="text-xs text-muted-foreground">({rel.to.ciId})</span>
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p className="text-sm text-muted-foreground">No dependencies.</p>
                    )}
                  </div>
                </div>

                <div className="rounded-lg border p-4">
                  <h3 className="mb-2 text-sm font-semibold">Active Incidents</h3>
                  {selected.issues.filter((i) => i.issue.status === 'OPEN' || i.issue.status === 'IN_PROGRESS').length ? (
                    <ul className="space-y-2 text-sm">
                      {selected.issues
                        .filter((i) => i.issue.status === 'OPEN' || i.issue.status === 'IN_PROGRESS')
                        .map((i) => (
                          <li key={i.issue.id} className="flex items-center justify-between">
                            <span>{i.issue.ticketId} {i.issue.title}</span>
                            <span className="rounded border px-2 py-0.5 text-xs">{i.issue.status}</span>
                          </li>
                        ))}
                    </ul>
                  ) : (
                    <p className="text-sm text-muted-foreground">No active incidents linked to this CI.</p>
                  )}
                </div>
              </div>
            )}
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  )
}
