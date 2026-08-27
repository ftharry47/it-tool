import { useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useMsal, useIsAuthenticated } from '@azure/msal-react'

export function Dashboard() {
  const { instance, accounts } = useMsal()
  const isAuthenticated = useIsAuthenticated()
  const navigate = useNavigate()

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login')
    }
  }, [isAuthenticated, navigate])

  const handleLogout = () => {
    instance.logoutRedirect({ postLogoutRedirectUri: '/' })
  }

  return (
    <div className="flex h-screen w-full items-center justify-center bg-background px-4">
      <div className="w-full max-w-2xl space-y-6 rounded-xl border border-border bg-card p-8 shadow-sm">
        <div className="space-y-2">
          <h1 className="text-2xl font-semibold tracking-tight text-card-foreground">
            Dashboard
          </h1>
          <p className="text-sm text-muted-foreground">
            Phase 1 identity shell is active. User profile will be loaded from <code>/api/auth/me</code>.
          </p>
        </div>

        <div className="rounded-md bg-muted p-4 text-sm font-mono text-muted-foreground">
          {accounts[0]?.username ?? 'Not signed in'}
        </div>

        <div className="flex gap-3">
          <Link
            to="/dashboard/incidents"
            className="inline-flex flex-1 items-center justify-center gap-2 rounded-md bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            View Incidents
          </Link>
          <button
            onClick={handleLogout}
            className="inline-flex flex-1 items-center justify-center gap-2 rounded-md bg-destructive px-4 py-2.5 text-sm font-medium text-destructive-foreground transition hover:bg-destructive/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            Sign out
          </button>
        </div>
      </div>
    </div>
  )
}
