import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { Users, Settings, Workflow, Bot, MapPin, HelpCircle, FileSpreadsheet, RotateCcw } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'

interface ResetResult {
  dryRun: boolean
  wipedByType: Record<string, number>
  totalWiped: number
  recreatedByType: Record<string, number>
  totalRecreated: number
}

/** SUPER_ADMIN-only: wipe all SLA tracking state, keeping the tickets. */
function ResetSlaCard() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const [open, setOpen] = useState(false)
  const [preview, setPreview] = useState<ResetResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState<ResetResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  const call = async (dryRun: boolean): Promise<ResetResult> => {
    const res = await fetchWithToken(instance, account!, `/api/v1/admin/maintenance/reset-sla?dryRun=${dryRun}`, { method: 'POST' })
    if (!res.ok) throw new Error((await res.text()) || `HTTP ${res.status}`)
    return res.json()
  }

  const openPreview = async () => {
    setBusy(true)
    setError(null)
    try {
      setPreview(await call(true))
      setOpen(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Preview failed')
    } finally {
      setBusy(false)
    }
  }

  const commit = async () => {
    setBusy(true)
    setError(null)
    try {
      setDone(await call(false))
      setOpen(false)
      setPreview(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Reset failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="rounded-md border border-destructive/40 bg-card p-4">
      <RotateCcw className="mb-2 h-5 w-5 text-destructive" />
      <p className="text-sm font-medium">Reset SLA Tracking</p>
      <p className="mt-1 text-xs text-muted-foreground">
        Wipes all SLA instances and recreates fresh ones for open tickets. Tickets, policies, and audit history are kept.
      </p>
      {done && (
        <p className="mt-2 text-xs text-emerald-600 dark:text-emerald-400">
          Done — wiped {done.totalWiped}, recreated {done.totalRecreated}.
        </p>
      )}
      {error && <p className="mt-2 text-xs text-destructive">{error}</p>}
      <button
        onClick={openPreview}
        disabled={busy}
        className="mt-3 w-full rounded-md border border-destructive/40 px-3 py-1.5 text-xs font-medium text-destructive transition hover:bg-destructive/10 disabled:opacity-50"
      >
        Preview reset…
      </button>

      <ConfirmDialog
        open={open}
        title="Reset all SLA tracking?"
        description={
          preview
            ? `This permanently deletes ${preview.totalWiped} SLA instance(s) — ${Object.entries(preview.wipedByType).map(([k, v]) => `${v} ${k}`).join(', ')} — and immediately creates fresh instances for ${preview.totalRecreated} open ticket(s). Reporting history starts over from today. Tickets, policies, escalation tiers, and audit logs are NOT touched.`
            : ''
        }
        confirmLabel="Reset SLA tracking"
        destructive
        pending={busy}
        onConfirm={commit}
        onCancel={() => { setOpen(false); setPreview(null) }}
      />
    </div>
  )
}

export function AdminHome() {
  const { currentUser } = useAuth()
  const isSuperAdmin = currentUser?.roles.includes('SUPER_ADMIN') ?? false

  const superAdminTiles = [
    { to: '/admin/users', icon: Users, label: 'Users' },
    { to: '/admin/catalog', icon: Settings, label: 'Catalog' },
    { to: '/admin/workflows', icon: Workflow, label: 'Workflows' },
    { to: '/admin/automation', icon: Bot, label: 'Automation' },
    { to: '/admin/locations', icon: MapPin, label: 'Locations' },
  ]
  const sharedTiles = [
    { to: '/admin/import-tickets', icon: FileSpreadsheet, label: 'Import Tickets' },
    { to: '/admin/how-it-works', icon: HelpCircle, label: 'How It Works' },
  ]
  const tiles = isSuperAdmin ? [...superAdminTiles, ...sharedTiles] : sharedTiles

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-4xl space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">Administration</h1>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {tiles.map(({ to, icon: Icon, label }) => (
            <Link
              key={to}
              to={to}
              className="rounded-md border border-border bg-card p-4 text-sm font-medium hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring"
            >
              <Icon className="mb-2 h-5 w-5" />
              {label}
            </Link>
          ))}
        </div>
        {isSuperAdmin && (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <ResetSlaCard />
          </div>
        )}
      </div>
    </div>
  )
}
