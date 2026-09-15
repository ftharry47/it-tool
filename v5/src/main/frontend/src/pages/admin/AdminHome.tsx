import { Link } from 'react-router-dom'
import { Users, Settings, Workflow, Bot, MapPin, HelpCircle, FileSpreadsheet } from 'lucide-react'
import { useAuth } from '../../auth/AuthProvider'

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
      </div>
    </div>
  )
}
