import { Outlet } from 'react-router-dom'
import { useAuth } from '../../auth/AuthProvider'
import { getDefaultRoute } from '../../routes/utils'
import { RoleNav } from './RoleNav'
import { Header } from './Header'

export function AppLayout() {
  const { currentUser } = useAuth()

  return (
    <div className="flex h-screen w-full bg-background text-foreground">
      <aside className="w-64 border-r border-border bg-card">
        <div className="border-b border-border p-4">
          <h2 className="text-lg font-semibold tracking-tight">ITSM Portal</h2>
          <p className="truncate text-xs text-muted-foreground">{currentUser?.displayName ?? currentUser?.email}</p>
        </div>
        <RoleNav />
        <div className="absolute bottom-0 w-64 border-t border-border p-3">
          <p className="text-xs text-muted-foreground text-center">© 2026 Srihari Thangavel. All rights reserved.</p>
        </div>
      </aside>
      <main className="flex flex-1 flex-col overflow-auto">
        <Header />
        <Outlet />
      </main>
    </div>
  )
}

export function useDefaultRoute() {
  const { currentUser } = useAuth()
  return getDefaultRoute(currentUser?.roles ?? [])
}
