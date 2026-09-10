import { useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { AlertTriangle, ChevronDown, LogIn } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { loginRequest } from '../../auth/authConfig'
import { BrandMark } from '../../components/theme/BrandMark'
import { useTheme } from '../../components/theme/ThemeProvider'
import { useDocumentTitle } from '../../components/layout/useDocumentTitle'

export function Login() {
  const { instance, inProgress } = useMsal()
  const { resolvedTheme } = useTheme()
  const [searchParams] = useSearchParams()
  const timedOut = searchParams.get('timeout') === '1'
  const busy = inProgress !== 'none'
  useDocumentTitle('Login')

  const [email, setEmail] = useState('')
  const [emailError, setEmailError] = useState<string | null>(null)

  const isValidEmail = (value: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())

  const handleLogin = () => {
    if (busy) return
    instance.loginRedirect(loginRequest)
  }

  const handleNext = () => {
    if (busy) return
    const trimmed = email.trim()
    if (!isValidEmail(trimmed)) {
      setEmailError('Please enter a valid email address.')
      return
    }
    setEmailError(null)
    instance.loginRedirect({ ...loginRequest, loginHint: trimmed })
  }

  return (
    <div className="flex h-screen w-full flex-col overflow-hidden bg-background md:flex-row">
      {/* Organisation logo — subtle "hosted for" mark, page-level top-left.
          Theme-aware variant; stays visible when the illustration panel
          hides below md. */}
      <div className="absolute left-6 top-5 z-10 bg-transparent p-1">
        <img
          src={resolvedTheme === 'dark' ? '/org-logo-dark.png' : '/org-logo-light.png'}
          alt="AlignedCardio"
          className="h-14 w-auto max-w-[280px] object-contain"
        />
      </div>

      {/* Branding side — illustration panel, LEFT column */}
      <div
        className="relative order-1 hidden w-full items-center justify-center overflow-hidden bg-muted bg-cover bg-center p-8 md:flex md:w-1/2 md:p-12 bg-[url(/login-bg-light.png)] dark:bg-[url(/login-bg-dark.png)]"
      />

      {/* Form side — RIGHT column */}
      <div className="order-2 flex w-full flex-col overflow-y-auto p-6 scrollbar-hidden md:w-1/2 md:p-12">
        <div className="m-auto w-full max-w-sm space-y-6">
          {timedOut && (
            <div className="flex items-start gap-3 rounded-md border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <div>
                <p className="font-medium">Session timed out</p>
                <p className="text-xs">You were signed out after 15 minutes of inactivity. Please sign in again.</p>
              </div>
            </div>
          )}

          {/* Wordmark header */}
          <BrandMark />

          {/* Organisation context (single-tenant, locked) */}
          <div className="space-y-1.5">
            <label htmlFor="login-org" className="text-sm font-medium">
              Organisation
            </label>
            <div className="relative">
              <select
                id="login-org"
                disabled
                className="w-full appearance-none rounded-md border border-input bg-muted px-3 py-2 text-sm text-muted-foreground disabled:cursor-not-allowed disabled:opacity-70"
              >
                <option>Aligned Cardiovascular Partners</option>
              </select>
              <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground/60" />
            </div>
          </div>

          <div className="space-y-3">
            <div className="space-y-1.5">
              <label htmlFor="login-email" className="text-sm font-medium">
                Email Address
              </label>
              <input
                id="login-email"
                type="email"
                autoComplete="off"
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value)
                  if (emailError) setEmailError(null)
                }}
                placeholder="name@company.com"
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              />
              {emailError && <p className="text-xs text-destructive">{emailError}</p>}
            </div>

            <button
              type="button"
              disabled={busy}
              onClick={handleNext}
              className="inline-flex w-full items-center justify-center rounded-md bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground transition-all duration-150 hover:bg-primary/90 active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-60"
            >
              {busy ? 'Signing in…' : 'Next'}
            </button>
          </div>

          {/* Divider */}
          <div className="flex items-center gap-3">
            <span className="h-px flex-1 bg-border" />
            <span className="text-xs text-muted-foreground">or</span>
            <span className="h-px flex-1 bg-border" />
          </div>

          {/* Real authentication */}
          <div className="space-y-1.5">
            <button
              onClick={handleLogin}
              disabled={busy}
              className="inline-flex w-full items-center justify-center gap-2 rounded-md bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground transition-all duration-150 hover:bg-primary/90 active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-60"
            >
              <LogIn className="h-4 w-4" />
              {busy ? 'Signing in…' : 'Sign in with Microsoft'}
            </button>
            <p className="text-center text-xs text-muted-foreground">
              Use your Aligned Cardiovascular Partners Microsoft account (firstname.lastname@alignedcardio.com)
            </p>
          </div>

          {/* Need Help? */}
          <details className="group">
            <summary className="flex cursor-pointer list-none items-center gap-1 text-xs font-medium text-muted-foreground transition hover:text-foreground [&::-webkit-details-marker]:hidden">
              Need Help?
              <ChevronDown className="h-3.5 w-3.5 transition-transform group-open:rotate-180" />
            </summary>
            <div className="mt-2 space-y-1 text-xs text-muted-foreground">
              <p>
                Browse the{' '}
                <Link to="/home/kb" className="text-foreground underline-offset-2 hover:underline">
                  knowledge base
                </Link>{' '}
                or contact IT support if you can't sign in.
              </p>
            </div>
          </details>

          {/* Server region (locked) */}
          <div className="space-y-1.5">
            <label htmlFor="login-server" className="text-sm font-medium">
              Server
            </label>
            <div className="relative">
              <select
                id="login-server"
                disabled
                className="w-full appearance-none rounded-md border border-input bg-muted px-3 py-2 text-sm text-muted-foreground disabled:cursor-not-allowed disabled:opacity-70"
              >
                <option>Canada Central</option>
              </select>
              <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground/60" />
            </div>
          </div>

          {/* Enterprise SSO (not available) */}
          <button
            type="button"
            disabled
            className="inline-flex w-full items-center justify-center rounded-md border border-border bg-muted px-4 py-2.5 text-sm font-medium text-muted-foreground disabled:cursor-not-allowed disabled:opacity-70"
          >
            Enterprise SSO Login
          </button>

          <p className="text-center text-xs text-muted-foreground">
            © 2026 Azentro by Sri Hari Thangavel
          </p>
        </div>
      </div>
    </div>
  )
}
