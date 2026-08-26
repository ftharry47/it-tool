import Link from 'next/link'
import { LifeBuoy, LayoutDashboard, Ticket } from 'lucide-react'

export default function HomePage() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-8 p-6">
      <div className="max-w-2xl text-center space-y-4">
        <p className="font-mono text-xs uppercase tracking-widest text-muted-foreground">
          AlignedCardio Portal v0.1
        </p>
        <h1 className="text-4xl font-semibold tracking-tight">
          Enterprise Work Management
        </h1>
        <p className="text-muted-foreground">
          ServiceNow-style ITIL and Jira-style Agile in one stack.
        </p>
      </div>

      <div className="grid w-full max-w-2xl gap-4 sm:grid-cols-3">
        <Link
          href="/dashboard/submit"
          className="group rounded-lg border px-6 py-8 transition-colors hover:border-foreground"
        >
          <div className="space-y-2">
            <p className="font-mono text-xs uppercase text-muted-foreground">User</p>
            <h2 className="text-lg font-medium flex items-center gap-2"><Ticket className="h-4 w-4" /> Submit a Ticket</h2>
            <p className="text-sm text-muted-foreground">
              Open the ticket form to report incidents, bugs, or requests.
            </p>
          </div>
        </Link>

        <Link
          href="/help-center"
          className="group rounded-lg border px-6 py-8 transition-colors hover:border-foreground"
        >
          <div className="space-y-2">
            <p className="font-mono text-xs uppercase text-muted-foreground">User</p>
            <h2 className="text-lg font-medium flex items-center gap-2"><LifeBuoy className="h-4 w-4" /> Help Center</h2>
            <p className="text-sm text-muted-foreground">
              Track requests, search the knowledge base, and message IT.
            </p>
          </div>
        </Link>

        <Link
          href="/dashboard/issues"
          className="group rounded-lg border px-6 py-8 transition-colors hover:border-foreground"
        >
          <div className="space-y-2">
            <p className="font-mono text-xs uppercase text-muted-foreground">IT / Dev</p>
            <h2 className="text-lg font-medium flex items-center gap-2"><LayoutDashboard className="h-4 w-4" /> Work Dashboard</h2>
            <p className="text-sm text-muted-foreground">
              Manage issues, epics, sprints, and the CMDB.
            </p>
          </div>
        </Link>
      </div>
    </main>
  )
}
