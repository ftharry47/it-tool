import { useAuth } from '../../auth/AuthProvider'
import { getRouteDefinitions } from '../../routes/index'
import { highestRole } from '../../routes/utils'
import { NavLink } from './NavLink'

const routeIcons: Record<string, string> = {}

export function RoleNav() {
  const { currentUser } = useAuth()
  const role = highestRole(currentUser?.roles ?? [])
  const routes = getRouteDefinitions(role)

  const navRoutes = routes.filter((route) => !route.path.includes(':') && !route.hidden)

  let adminReached = false

  return (
    <nav aria-label="Main" className="space-y-1 p-3">
      {navRoutes.map((route, index) => {
        const isAdmin = route.path.startsWith('admin')
        const separator = !adminReached && isAdmin ? (
          <div key={`admin-sep-${index}`} className="my-2 border-t border-border pt-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            Administration
          </div>
        ) : null
        adminReached = adminReached || isAdmin
        return (
          <>
            {separator}
            <NavLink
              key={route.path}
              to={`/${route.path}`}
              label={route.label}
              icon={routeIcons[route.path] ? <span className="w-4 text-center">{routeIcons[route.path]}</span> : undefined}
            />
          </>
        )
      })}
    </nav>
  )
}
