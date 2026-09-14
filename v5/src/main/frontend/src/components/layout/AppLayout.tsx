import { useEffect, useMemo, useRef } from 'react'
import { matchPath, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../../auth/AuthProvider'
import { getDefaultRoute, highestRole } from '../../routes/utils'
import { getRouteDefinitions } from '../../routes'
import { RoleNav } from './RoleNav'
import { Header } from './Header'
import { BrandMark } from '../theme/BrandMark'
import { useDocumentTitle } from './useDocumentTitle'

export function AppLayout() {
  const location = useLocation()
  const { currentUser } = useAuth()
  const mainRef = useRef<HTMLElement>(null)

  // Reset the content scroll position on navigation — `main` is the scroll
  // container and isn't remounted between routes, so scrollTop would
  // otherwise persist and land the user mid-page.
  useEffect(() => {
    mainRef.current?.scrollTo({ top: 0 })
  }, [location.pathname])

  // Dynamic tab title: "Azentro | <route label>". Most specific match wins
  // (e.g. 'changes/calendar' beats 'changes/:id'). Detail pages may override
  // with a more specific title once entity data loads.
  const pageLabel = useMemo(() => {
    const defs = getRouteDefinitions(highestRole(currentUser?.roles ?? []), currentUser)
    const path = location.pathname.replace(/^\/+/, '')
    let best: { label: string; statics: number } | null = null
    for (const def of defs) {
      if (!matchPath({ path: def.path, end: true }, path)) continue
      const statics = def.path.split('/').filter((s) => s && !s.startsWith(':')).length
      if (!best || statics > best.statics) best = { label: def.label, statics }
    }
    return best?.label ?? null
  }, [location.pathname, currentUser?.roles, currentUser?.isApprovalManager])
  useDocumentTitle(pageLabel)

  return (
    <div className="flex h-screen w-full overflow-hidden bg-background text-foreground">
      <aside className="flex h-full w-64 flex-col border-r border-border bg-card">
        <div className="border-b border-border p-4">
          <BrandMark />
        </div>
        <div className="flex-1 overflow-y-auto scrollbar-hidden">
          <RoleNav />
        </div>
        <div className="border-t border-border p-3">
          <p className="text-xs text-muted-foreground text-center">© 2026 Azentro by Sri Hari Thangavel</p>
        </div>
      </aside>
      {/* `relative` makes <main> the containing block for absolutely positioned
          descendants (e.g. sr-only table captions); without it they escape the
          overflow clip and inflate the document's scroll height, producing a
          second window-level scroll into blank space. `min-h-0` is the explicit
          cross-axis clamp so this flex item never outgrows the 100vh shell. */}
      <main ref={mainRef} className="relative flex min-h-0 flex-1 flex-col overflow-auto scrollbar-hidden">
        <Header />
        <div key={location.pathname} className="animate-fade-slide-up flex flex-1 flex-col">
          <Outlet />
        </div>
      </main>
    </div>
  )
}

export function useDefaultRoute() {
  const { currentUser } = useAuth()
  return getDefaultRoute(currentUser?.roles ?? [])
}
