import { Link } from 'react-router-dom'
import { Users, Settings, Workflow, Bot, MapPin, HelpCircle } from 'lucide-react'

export function AdminHome() {
  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-4xl space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">Administration</h1>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Link to="/admin/users" className="rounded-md border border-border bg-card p-4 text-sm font-medium hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring">
            <Users className="mb-2 h-5 w-5" />
            Users
          </Link>
          <Link to="/admin/catalog" className="rounded-md border border-border bg-card p-4 text-sm font-medium hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring">
            <Settings className="mb-2 h-5 w-5" />
            Catalog
          </Link>
          <Link to="/admin/workflows" className="rounded-md border border-border bg-card p-4 text-sm font-medium hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring">
            <Workflow className="mb-2 h-5 w-5" />
            Workflows
          </Link>
          <Link to="/admin/automation" className="rounded-md border border-border bg-card p-4 text-sm font-medium hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring">
            <Bot className="mb-2 h-5 w-5" />
            Automation
          </Link>
          <Link to="/admin/locations" className="rounded-md border border-border bg-card p-4 text-sm font-medium hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring">
            <MapPin className="mb-2 h-5 w-5" />
            Locations
          </Link>
          <Link to="/admin/how-it-works" className="rounded-md border border-border bg-card p-4 text-sm font-medium hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring">
            <HelpCircle className="mb-2 h-5 w-5" />
            How It Works
          </Link>
        </div>
      </div>
    </div>
  )
}
