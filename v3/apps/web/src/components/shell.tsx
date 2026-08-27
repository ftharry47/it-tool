'use client'

import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { useCommandPalette } from '@/store/command-palette'
import { useAuth } from '@/lib/api'
import { cn } from '@/lib/utils'
import { Command, LayoutDashboard, LifeBuoy, Ticket, Workflow, Settings, LogOut, User } from 'lucide-react'
import { Button } from './button'

const nav = [
  { label: 'Dashboard', href: '/dashboard/issues', icon: LayoutDashboard },
  { label: 'Issues', href: '/dashboard/issues', icon: Ticket },
  { label: 'Workflows', href: '/dashboard/workflows', icon: Workflow },
  { label: 'Help Center', href: '/help-center', icon: LifeBuoy },
  { label: 'Settings', href: '/dashboard/settings', icon: Settings },
]

export function Shell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const router = useRouter()
  const { toggle } = useCommandPalette()
  const { user, loaded, logout } = useAuth()

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-40 border-b bg-background/95 backdrop-blur">
        <div className="flex h-14 items-center gap-4 px-4">
          <Link href="/" className="flex items-center gap-2 font-semibold">
            AlignedCardio
          </Link>

          <nav className="hidden flex-1 items-center gap-1 md:flex">
            {nav.map((item) => {
              const active = pathname.startsWith(item.href)
              const Icon = item.icon
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={cn(
                    'flex items-center gap-2 rounded-md px-3 py-1.5 text-sm transition-colors',
                    active ? 'bg-accent text-accent-foreground' : 'text-muted-foreground hover:text-foreground'
                  )}
                >
                  <Icon className="h-4 w-4" />
                  {item.label}
                </Link>
              )
            })}
          </nav>

          <button
            onClick={toggle}
            className="flex items-center gap-2 rounded-md border px-3 py-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground"
          >
            <Command className="h-4 w-4" />
            <span className="hidden sm:inline">Command</span>
            <kbd className="hidden rounded bg-muted px-1.5 py-0.5 text-xs font-mono sm:inline">⌘K</kbd>
          </button>

          {loaded && user ? (
            <div className="hidden items-center gap-3 sm:flex">
              <div className="text-right text-xs leading-tight">
                <p className="font-medium">{user.name}</p>
                <p className="text-muted-foreground">{user.email}</p>
              </div>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  logout()
                  router.push('/')
                }}
                className="flex items-center gap-1.5 px-2"
              >
                <LogOut className="h-4 w-4" />
                <span className="hidden lg:inline">Sign out</span>
              </Button>
            </div>
          ) : loaded ? (
            <Link href="/login" className="text-sm font-medium text-primary hover:underline">
              Sign in
            </Link>
          ) : null}
        </div>
      </header>
      <main className="flex-1 p-6">{children}</main>
    </div>
  )
}
