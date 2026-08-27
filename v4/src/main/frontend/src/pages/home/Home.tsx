import { Link } from 'react-router-dom'
import { Ticket, BookOpen, LayoutGrid } from 'lucide-react'

export function Home() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4">
      <div className="w-full max-w-2xl space-y-6 rounded-xl border border-border bg-card p-8 shadow-sm">
        <h1 className="text-2xl font-semibold tracking-tight text-card-foreground">Self-Service Portal</h1>
        <p className="text-sm text-muted-foreground">Get help, track requests, and find answers.</p>
        <div className="grid gap-3 sm:grid-cols-2">
          <Link
            to="/home/incidents"
            className="inline-flex items-center gap-2 rounded-md border border-border bg-background p-4 text-sm font-medium hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring"
          >
            <Ticket className="h-4 w-4" />
            My Incidents
          </Link>
          <Link
            to="/home/catalog"
            className="inline-flex items-center gap-2 rounded-md border border-border bg-background p-4 text-sm font-medium hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring"
          >
            <LayoutGrid className="h-4 w-4" />
            Service Catalog
          </Link>
          <Link
            to="/home/kb"
            className="inline-flex items-center gap-2 rounded-md border border-border bg-background p-4 text-sm font-medium hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring sm:col-span-2"
          >
            <BookOpen className="h-4 w-4" />
            Knowledge Base
          </Link>
        </div>
      </div>
    </div>
  )
}
