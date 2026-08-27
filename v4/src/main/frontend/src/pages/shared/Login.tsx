import { useMsal } from '@azure/msal-react'
import { LogIn } from 'lucide-react'

export function Login() {
  const { instance } = useMsal()

  const handleLogin = () => {
    instance.loginRedirect({
      scopes: ['openid', 'profile', 'email', 'User.Read'],
    })
  }

  return (
    <div className="flex h-screen w-full items-center justify-center bg-background px-4">
      <div className="w-full max-w-sm space-y-6 rounded-xl border border-border bg-card p-8 shadow-sm">
        <div className="space-y-2 text-center">
          <h1 className="text-2xl font-semibold tracking-tight text-card-foreground">
            ITSM Portal
          </h1>
          <p className="text-sm text-muted-foreground">
            Sign in with your Microsoft account to continue.
          </p>
        </div>

        <button
          onClick={handleLogin}
          className="inline-flex w-full items-center justify-center gap-2 rounded-md bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <LogIn className="h-4 w-4" />
          Sign in with Microsoft
        </button>
      </div>
    </div>
  )
}
