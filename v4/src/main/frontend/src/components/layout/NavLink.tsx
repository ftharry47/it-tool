import { NavLink as RouterNavLink } from 'react-router-dom'

interface NavLinkProps {
  to: string
  label: string
  icon?: React.ReactNode
}

export function NavLink({ to, label, icon }: NavLinkProps) {
  return (
    <RouterNavLink
      to={to}
      className={({ isActive }) =>
        `flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
          isActive
            ? 'bg-primary text-primary-foreground'
            : 'text-muted-foreground hover:bg-muted hover:text-foreground'
        }`
      }
    >
      {icon}
      {label}
    </RouterNavLink>
  )
}
