import { useAuth } from '../../auth/AuthProvider'
import { getRouteDefinitions } from '../../routes/index'
import { highestRole } from '../../routes/utils'
import { NavLink } from './NavLink'

const routeIcons: Record<string, string> = {}

export function RoleNav() {
  const { currentUser } = useAuth()
  const role = highestRole(currentUser?.roles ?? [])
  const routes = getRouteDefinitions(role)

  return (
    <nav aria-label="Main" className="space-y-1 p-3">
      {routes.map((route) => (
        <NavLink
          key={route.path}
          to={`/${route.path}`}
          label={route.label}
          icon={routeIcons[route.path] ? <span className="w-4 text-center">{routeIcons[route.path]}</span> : undefined}
        />
      ))}
    </nav>
  )
}
