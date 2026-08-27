import Link from 'next/link'
import { BadgeInfo, CircleDot, Clock3, LifeBuoy, LayoutDashboard, Server, Ticket } from 'lucide-react'

export default function HomePage() {
  return (
    <main className="relative flex min-h-screen flex-col items-center justify-center gap-8 overflow-hidden bg-black p-6">
      <div className="pointer-events-none absolute inset-0 z-0 flex items-center justify-center">
        <div className="h-[55vh] w-[55vh] rounded-full bg-[radial-gradient(circle_at_center,_rgba(250,204,21,0.09),_transparent_70%)] blur-3xl animate-pulse" />
        <div
          className="absolute h-[35vh] w-[35vh] rounded-full bg-[radial-gradient(circle_at_center,_rgba(59,130,246,0.07),_transparent_70%)] blur-3xl animate-pulse"
          style={{ animationDelay: '2s' }}
        />
      </div>

      <div className="relative z-10 max-w-2xl text-center">
        <h1 className="text-4xl font-semibold tracking-tight text-white">
          Enterprise Work Management
        </h1>
      </div>

      <div className="relative z-10 grid w-full max-w-3xl gap-4 sm:grid-cols-3">
        <Link
          href="/dashboard/submit"
          className="group flex h-full w-full flex-col rounded-lg border border-white/10 bg-black/40 px-6 py-8 transition-colors hover:border-white/30"
        >
          <div className="space-y-2">
            <h2 className="text-lg font-medium text-white flex items-center gap-2"><Ticket className="h-4 w-4" /> Submit a Ticket</h2>
            <p className="text-sm text-muted-foreground">
              Open the ticket form to report incidents, bugs, or requests.
            </p>
          </div>
        </Link>

        <Link
          href="/help-center"
          className="group flex h-full w-full flex-col rounded-lg border border-white/10 bg-black/40 px-6 py-8 transition-colors hover:border-white/30"
        >
          <div className="space-y-2">
            <h2 className="text-lg font-medium text-white flex items-center gap-2"><LifeBuoy className="h-4 w-4" /> Help Center</h2>
            <p className="text-sm text-muted-foreground">
              Track requests, search the knowledge base, and message IT.
            </p>
          </div>
        </Link>

        <Link
          href="/dashboard/issues"
          className="group flex h-full w-full flex-col rounded-lg border border-white/10 bg-black/40 px-6 py-8 transition-colors hover:border-white/30"
        >
          <div className="space-y-2">
            <h2 className="text-lg font-medium text-white flex items-center gap-2"><LayoutDashboard className="h-4 w-4" /> Work Dashboard</h2>
            <p className="text-sm text-muted-foreground">
              Manage issues, epics, sprints, and the CMDB.
            </p>
          </div>
        </Link>
      </div>

      <div className="relative z-10 w-full max-w-3xl rounded-2xl border border-white/10 bg-black/40 p-5">
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div className="flex items-center gap-2">
            <CircleDot className="h-4 w-4 text-emerald-500" strokeWidth={1.5} />
            <div>
              <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Status</p>
              <p className="text-sm font-medium text-white">Operational</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <BadgeInfo className="h-4 w-4 text-zinc-400" strokeWidth={1.5} />
            <div>
              <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Version</p>
              <p className="text-sm font-medium text-white">0.1</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Server className="h-4 w-4 text-zinc-400" strokeWidth={1.5} />
            <div>
              <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Environment</p>
              <p className="text-sm font-medium text-white">Production</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Clock3 className="h-4 w-4 text-zinc-400" strokeWidth={1.5} />
            <div>
              <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Updated</p>
              <p className="text-sm font-medium text-white">10:30 AM</p>
            </div>
          </div>
        </div>
      </div>
    </main>
  )
}
