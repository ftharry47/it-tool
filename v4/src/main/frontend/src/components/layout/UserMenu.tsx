import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { LogOut, Settings, User } from 'lucide-react'
import { useAuth } from '../../auth/AuthProvider'
import { ThemeToggle } from '../theme/ThemeToggle'
import { ConfirmDialog } from '../ui/ConfirmDialog'

export function UserMenu() {
  const { accounts } = useMsal()
  const { currentUser, logout } = useAuth()
  const account = accounts[0]
  const [open, setOpen] = useState(false)
  const [confirmSignOut, setConfirmSignOut] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  const displayName = currentUser?.displayName ?? account?.name ?? currentUser?.email ?? 'User'
  const email = currentUser?.email ?? account?.username ?? ''
  const initials = displayName
    .split(' ')
    .slice(0, 2)
    .map((n) => n[0])
    .join('')
    .toUpperCase()

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])


  const handleSignOut = () => {
    setOpen(false)
    setConfirmSignOut(true)
  }

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-2 rounded-md border border-border bg-background px-3 py-2 text-sm font-medium transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        aria-label="Account menu"
        aria-haspopup="menu"
        aria-expanded={open}
      >
        <span className="flex h-6 w-6 items-center justify-center rounded-full bg-primary text-xs font-semibold text-primary-foreground">
          {initials || <User className="h-3 w-3" />}
        </span>
        <span className="hidden max-w-[120px] truncate md:inline">{displayName}</span>
      </button>

      {open && (
        <div className="animate-zoom-in-95 absolute right-0 z-50 mt-2 w-64 origin-top-right rounded-xl border border-border bg-card p-2 shadow-lg">
          <div className="mb-2 flex items-center gap-3 border-b border-border p-2">
            <span className="flex h-10 w-10 items-center justify-center rounded-full bg-primary text-sm font-semibold text-primary-foreground">
              {initials || <User className="h-4 w-4" />}
            </span>
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{displayName}</p>
              <p className="truncate text-xs text-muted-foreground">{email}</p>
            </div>
          </div>

          <Link
            to="/dashboard/settings"
            onClick={() => setOpen(false)}
            className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm transition hover:bg-muted"
          >
            <Settings className="h-4 w-4" />
            Settings
          </Link>
          <ThemeToggle className="text-left" />
          <button
            onClick={handleSignOut}
            className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm text-destructive transition hover:bg-destructive/10"
          >
            <LogOut className="h-4 w-4" />
            Sign out
          </button>
        </div>
      )}

      <ConfirmDialog
        open={confirmSignOut}
        title="Sign out?"
        description="Are you sure you want to sign out?"
        confirmLabel="Sign Out"
        cancelLabel="Cancel"
        destructive
        onConfirm={() => {
          setConfirmSignOut(false)
          logout()
        }}
        onCancel={() => setConfirmSignOut(false)}
      />
    </div>
  )
}
