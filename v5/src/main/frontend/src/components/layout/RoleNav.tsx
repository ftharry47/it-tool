import { useAuth } from '../../auth/AuthProvider'
import { getRouteDefinitions } from '../../routes/index'
import { highestRole } from '../../routes/utils'
import { NavLink } from './NavLink'

const routeIcons: Record<string, string> = {}

export function RoleNav() {
  const { currentUser } = useAuth()
  const role = highestRole(currentUser?.roles ?? [])
  const routes = getRouteDefinitions(role, currentUser)

  const navRoutes = routes.filter((route) => !route.path.includes(':') && !route.hidden)

  let lastGroup: string | undefined

  return (
    <nav aria-label="Main" className="space-y-1 p-3">
      {navRoutes.map((route) => {
        const header = route.group && route.group !== lastGroup ? (
          <div
            key={`group-${route.group}`}
            aria-hidden="true"
            className={`${lastGroup !== undefined ? 'mt-5 border-t border-border pt-5' : 'pt-1'} cursor-default select-none px-3 pb-2 text-[11px] font-medium uppercase tracking-[0.12em] text-muted-foreground/60`}
          >
            {route.group}
          </div>
        ) : null
        lastGroup = route.group ?? lastGroup
        return (
          <div key={route.path}>
            {header}
            <NavLink
              to={`/${route.path}`}
              label={route.label}
              icon={routeIcons[route.path] ? <span className="w-4 text-center">{routeIcons[route.path]}</span> : undefined}
            />
          </div>
        )
      })}
    </nav>
  )
}
