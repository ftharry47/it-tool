import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/AuthProvider'
import { getDefaultRoute } from '../../routes/utils'

export function NotFound() {
  const { currentUser } = useAuth()
  const home = getDefaultRoute(currentUser?.roles ?? [])
  return (
    <div className="flex h-full min-h-[50vh] flex-col items-center justify-center gap-4 p-6 text-center">
      <h1 className="text-4xl font-bold">404</h1>
      <p className="text-muted-foreground">This page is not available for your role.</p>
      <Link to={home} className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90">
        Go back
      </Link>
    </div>
  )
}
