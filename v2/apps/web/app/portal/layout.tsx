'use client'

import * as React from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useAuth } from '@/components/auth/AuthProvider'
import { Home, PlusCircle, ClipboardList, BookOpen, LayoutGrid, Shield, LogOut, CheckSquare } from 'lucide-react'

const nav = [
  { href: '/portal', label: 'Home', icon: Home },
  { href: '/portal/catalog', label: 'Catalog', icon: LayoutGrid },
  { href: '/portal/report', label: 'Report', icon: PlusCircle },
  { href: '/portal/requests', label: 'My Requests', icon: ClipboardList },
  { href: '/portal/kb', label: 'Knowledge Base', icon: BookOpen },
  { href: '/portal/approvals', label: 'Approvals', icon: CheckSquare, role: 'MANAGER' },
  { href: '/portal/admin/forms', label: 'Admin Forms', icon: Shield, role: 'ADMIN' },
]

export default function PortalLayout({ children }: { children: React.ReactNode }) {
  const { user, isLoading, logout } = useAuth()
  const pathname = usePathname()

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    )
  }

  if (!user) {
    return <LoginPrompt />
  }

  return (
    <div className="flex min-h-screen flex-col bg-background">
      <header className="border-b bg-card px-6 py-3">
        <div className="mx-auto flex max-w-6xl items-center justify-between">
          <Link href="/portal" className="text-lg font-bold text-primary">
            Dev-IT Portal
          </Link>
          <div className="flex items-center gap-4">
            <span className="text-sm text-muted-foreground">{user.name}</span>
            <span className="rounded-full bg-secondary px-2 py-0.5 text-xs font-medium text-secondary-foreground">
              {user.role}
            </span>
            <button onClick={logout} className="text-muted-foreground hover:text-foreground">
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </div>
      </header>
      <div className="mx-auto flex w-full max-w-6xl flex-1 gap-6 p-6">
        <aside className="hidden w-56 shrink-0 flex-col gap-1 md:flex">
          {nav
            .filter((n) => !n.role || user.role === n.role)
            .map((n) => (
              <Link
                key={n.href}
                href={n.href}
                className={`flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition ${
                  pathname === n.href
                    ? 'bg-primary text-primary-foreground'
                    : 'text-foreground hover:bg-secondary'
                }`}
              >
                <n.icon className="h-4 w-4" />
                {n.label}
              </Link>
            ))}
        </aside>
        <main className="min-w-0 flex-1">{children}</main>
      </div>
    </div>
  )
}

function LoginPrompt() {
  const { login } = useAuth()
  const [email, setEmail] = React.useState('employee1@devit.local')
  const [busy, setBusy] = React.useState(false)
  const [error, setError] = React.useState('')

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await login(email)
    } catch (err: any) {
      setError(err.message || 'Login failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="w-full max-w-sm rounded-xl border bg-card p-6 shadow-sm">
        <h1 className="text-2xl font-bold">Dev-IT Portal</h1>
        <p className="mt-2 text-sm text-muted-foreground">Sign in with your email to continue.</p>
        <form onSubmit={submit} className="mt-4 space-y-3">
          <input
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
            placeholder="email@devit.local"
          />
          {error && <p className="text-xs text-destructive">{error}</p>}
          <button
            type="submit"
            disabled={busy}
            className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
          >
            {busy ? 'Signing in...' : 'Sign in'}
          </button>
        </form>
        <p className="mt-4 text-xs text-muted-foreground">
          Try: employee1@devit.local, manager@devit.local, admin@devit.local
        </p>
      </div>
    </div>
  )
}
