import { useMsal } from '@azure/msal-react'
import { LogIn } from 'lucide-react'
import { loginRequest } from '../../auth/authConfig'

export function Login() {
  const { instance, inProgress } = useMsal()
  const busy = inProgress !== 'none'

  const handleLogin = () => {
    if (busy) return
    instance.loginRedirect(loginRequest)
  }

  return (
    <div className="flex h-screen w-full flex-col bg-background lg:flex-row">
      {/* Branding side */}
      <div
        className="relative order-1 flex w-full items-center justify-center overflow-hidden bg-muted p-8 lg:order-1 lg:w-1/2 lg:p-12"
        style={{
          backgroundImage: 'radial-gradient(circle, hsl(var(--primary) / 0.12) 1px, transparent 1px)',
          backgroundSize: '32px 32px',
        }}
      >
        <div className="relative z-10 text-center lg:text-left">
          <h1 className="text-4xl font-bold tracking-tight text-primary lg:text-5xl">
            AlignedCardio
          </h1>
          <p className="mt-3 text-sm text-muted-foreground lg:text-base">
            IT Service Management for Healthcare
          </p>
        </div>
      </div>

      {/* Form side */}
      <div className="order-2 flex w-full flex-col items-center justify-center p-6 lg:order-2 lg:w-1/2 lg:p-12">
        <div className="w-full max-w-sm space-y-6">
          <div className="space-y-2 text-center lg:hidden">
            <h2 className="text-2xl font-semibold tracking-tight text-card-foreground">AlignedCardio</h2>
            <p className="text-sm text-muted-foreground">IT Service Management for Healthcare</p>
          </div>

          <div className="space-y-2 text-center">
            <h2 className="text-2xl font-semibold tracking-tight text-card-foreground">Welcome back</h2>
            <p className="text-sm text-muted-foreground">Sign in with your Microsoft account to continue.</p>
          </div>

          <button
            onClick={handleLogin}
            disabled={busy}
            className="inline-flex w-full items-center justify-center gap-2 rounded-md bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-60"
          >
            <LogIn className="h-4 w-4" />
            {busy ? 'Signing in…' : 'Sign in with Microsoft'}
          </button>

          <p className="text-center text-xs text-muted-foreground">
            © 2026 Srihari Thangavel. All rights reserved.
          </p>
        </div>
      </div>
    </div>
  )
}
