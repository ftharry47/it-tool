import { Outlet } from 'react-router-dom'
import { useAuth } from '../../auth/AuthProvider'
import { getDefaultRoute } from '../../routes/utils'
import { RoleNav } from './RoleNav'

export function AppLayout() {
  const { currentUser, logout } = useAuth()

  return (
    <div className="flex h-screen w-full bg-background text-foreground">
      <aside className="w-64 border-r border-border bg-card">
        <div className="border-b border-border p-4">
          <h2 className="text-lg font-semibold tracking-tight">ITSM Portal</h2>
          <p className="truncate text-xs text-muted-foreground">{currentUser?.displayName ?? currentUser?.email}</p>
        </div>
        <RoleNav />
        <div className="absolute bottom-0 w-64 border-t border-border p-3">
          <button
            onClick={logout}
            className="w-full rounded-md bg-destructive px-3 py-2 text-sm font-medium text-destructive-foreground hover:bg-destructive/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            Sign out
          </button>
        </div>
      </aside>
      <main className="flex-1 overflow-auto">
        <Outlet />
      </main>
    </div>
  )
}

export function useDefaultRoute() {
  const { currentUser } = useAuth()
  return getDefaultRoute(currentUser?.roles ?? [])
}
