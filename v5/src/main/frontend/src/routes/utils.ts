const rolePriority: Record<string, number> = {
  END_USER: 0,
  AGENT: 1,
  TEAM_LEAD: 1,
  ADMIN: 2,
  SUPER_ADMIN: 2,
}

const defaultRoute: Record<string, string> = {
  END_USER: '/home',
  AGENT: '/dashboard',
  TEAM_LEAD: '/dashboard',
  ADMIN: '/admin',
  SUPER_ADMIN: '/admin',
}

export function highestRole(roles: string[]): string {
  if (!roles || roles.length === 0) return 'END_USER'
  return roles.reduce<string>((best, r) => {
    const p = rolePriority[r] ?? -1
    return p >= (rolePriority[best] ?? -1) ? r : best
  }, 'END_USER')
}

export function getDefaultRoute(roles: string[]): string {
  return defaultRoute[highestRole(roles)] ?? '/home'
}
