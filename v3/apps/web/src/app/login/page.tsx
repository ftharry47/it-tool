'use client'

import { Suspense, useMemo, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { useAuth } from '@/lib/api'
import { cn } from '@/lib/utils'
import { AxiomDeskFull, AxiomDeskSymbol } from '@/components/axiomdesk-logo'
import { AlertCircle, ArrowLeft, Check, CheckCircle, ChevronDown, Clock, Globe, MessageSquare, Ticket, Zap } from 'lucide-react'

function MicrosoftIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 23 23" className={cn('h-5 w-5', className)}>
      <path fill="#f25022" d="M1 1h9.5v9.5H1z" />
      <path fill="#7fba00" d="M12.5 1H22v9.5h-9.5z" />
      <path fill="#00a4ef" d="M1 12.5h9.5V22H1z" />
      <path fill="#ffb900" d="M12.5 12.5H22V22h-9.5z" />
    </svg>
  )
}

function StatusBadge({ color, children }: { color: 'green' | 'red'; children: React.ReactNode }) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide',
        color === 'green' ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'
      )}
    >
      {children}
    </span>
  )
}

function TicketRow({ id, title, badge, time, badgeColor }: { id: string; title: string; badge: string; time: string; badgeColor: 'green' | 'red' }) {
  return (
    <div className="flex items-start justify-between gap-3 border-b border-zinc-200 py-3 last:border-0 last:pb-0">
      <div className="min-w-0 flex-1">
        <p className="truncate text-xs font-semibold text-zinc-900">
          {id}: {title}
        </p>
      </div>
      <div className="flex flex-shrink-0 items-center gap-2">
        <StatusBadge color={badgeColor}>{badge}</StatusBadge>
        <span className="text-[10px] text-zinc-400">{time}</span>
      </div>
    </div>
  )
}

function LoginVisual() {
  return (
    <div className="login-perspective relative h-full w-full overflow-hidden bg-zinc-950">
      <div className="pointer-events-none absolute left-1/4 top-1/4 h-64 w-64 rounded-full bg-yellow-500/5 blur-3xl" />
      <div className="pointer-events-none absolute bottom-1/4 right-1/4 h-64 w-64 rounded-full bg-blue-500/5 blur-3xl" />

      <div className="login-board absolute left-1/2 top-1/2 h-80 w-80 rounded-2xl border border-zinc-800/80 bg-zinc-900/70 p-6">
        <div className="grid h-full w-full grid-cols-3 gap-3">
          <div className="login-float login-ticket-1 col-span-2 rounded-lg border border-zinc-800 bg-zinc-950/80 p-3 shadow-lg">
            <div className="flex items-center gap-2">
              <Ticket className="h-4 w-4 text-blue-500" strokeWidth={1.5} />
              <span className="text-xs font-semibold text-white">INC-1423</span>
            </div>
            <p className="mt-1 text-[10px] text-zinc-400">VPN access down</p>
          </div>
          <div className="login-float login-ticket-2 rounded-lg border border-zinc-800 bg-zinc-950/80 p-3 shadow-lg">
            <CheckCircle className="h-4 w-4 text-emerald-500" strokeWidth={1.5} />
            <span className="mt-1 block text-[10px] text-white">Resolved</span>
          </div>
          <div className="login-float login-ticket-3 rounded-lg border border-zinc-800 bg-zinc-950/80 p-3 shadow-lg">
            <Zap className="h-4 w-4 text-yellow-500" strokeWidth={1.5} />
            <span className="mt-1 block text-[10px] text-white">SLA 99%</span>
          </div>
          <div className="login-float login-ticket-1 col-span-3 rounded-lg border border-zinc-800 bg-zinc-950/80 p-3 shadow-lg">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <MessageSquare className="h-4 w-4 text-violet-500" strokeWidth={1.5} />
                <span className="text-xs font-semibold text-white">SupportAI</span>
              </div>
              <span className="text-[10px] text-zinc-400">12s</span>
            </div>
          </div>
        </div>

        <div className="login-pulse-node login-pulse-1 absolute right-8 top-8 h-3 w-3 rounded-full bg-yellow-500" />
        <div className="login-pulse-node login-pulse-2 absolute bottom-12 left-8 h-3 w-3 rounded-full bg-blue-500" />
        <div className="login-pulse-node login-pulse-3 absolute bottom-8 right-12 h-3 w-3 rounded-full bg-emerald-500" />

        <svg className="pointer-events-none absolute inset-0 h-full w-full" viewBox="0 0 320 320" fill="none">
          <path
            className="login-draw-line"
            d="M40 40 L160 160 L280 280"
            stroke="rgba(255,255,255,0.15)"
            strokeWidth="1"
            fill="none"
          />
          <path
            className="login-draw-line"
            d="M40 280 L160 160 L280 40"
            stroke="rgba(255,255,255,0.15)"
            strokeWidth="1"
            fill="none"
          />
        </svg>
      </div>
    </div>
  )
}

function LoginForm() {
  const { login, loaded } = useAuth()
  const router = useRouter()
  const searchParams = useSearchParams()
  const returnTo = searchParams.get('returnTo') || '/'

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [remember, setRemember] = useState(true)
  const [step, setStep] = useState<'email' | 'password'>('email')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [helpOpen, setHelpOpen] = useState(false)

  const isEmailValid = useMemo(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email), [email])

  const handleMicrosoft = () => {
    setError(null)
    alert('Microsoft SSO integration will be connected here.')
  }

  const handleContinue = (e: React.FormEvent) => {
    e.preventDefault()
    if (!isEmailValid) return
    setStep('password')
  }

  const handleSignIn = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!email || !password) return
    setLoading(true)
    setError(null)
    try {
      await login(email, password)
      router.push(returnTo)
    } catch (err: any) {
      setError(err.message || 'Sign in failed. Please check your credentials and try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="flex min-h-screen w-full bg-black">
      {/* Left column — authentication panel */}
      <section className="relative z-10 flex w-full flex-col bg-[#232323] px-6 py-10 text-zinc-100 lg:w-1/2 lg:px-16 lg:py-12">
        <a
          href="/"
          className="mb-4 flex items-center gap-2 text-sm text-zinc-400 hover:text-white transition"
        >
          <ArrowLeft className="h-4 w-4" /> Back
        </a>
        <div className="flex items-center justify-between">
          <AxiomDeskFull className="h-7" />
        </div>

        <div className="flex flex-1 flex-col items-center justify-center">
          <div className="w-full max-w-sm space-y-6">
            <div className="space-y-2">
              <h1 className="text-3xl font-bold tracking-tight text-white lg:text-4xl">
                Enterprise Work Management
              </h1>
              <p className="text-sm text-zinc-400">
                ServiceNow-style ITIL and Jira-style Agile in one stack.
              </p>
            </div>

            <button
              type="button"
              onClick={handleMicrosoft}
              disabled={!loaded || loading}
              className="flex w-full items-center justify-center gap-3 rounded-md bg-white py-2.5 text-sm font-semibold text-zinc-900 shadow-sm transition hover:bg-zinc-100 disabled:opacity-60"
            >
              <MicrosoftIcon />
              Sign in with Microsoft
            </button>

            <div className="flex items-center gap-3">
              <span className="h-px flex-1 bg-zinc-800" />
              <span className="text-xs text-zinc-500">or</span>
              <span className="h-px flex-1 bg-zinc-800" />
            </div>

            <form onSubmit={step === 'email' ? handleContinue : handleSignIn} className="space-y-4">
              {step === 'email' ? (
                <>
                  <div className="space-y-1.5">
                    <label htmlFor="email" className="text-xs font-medium text-zinc-300">
                      Work Email
                    </label>
                    <input
                      id="email"
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="Enter your work email"
                      className="w-full rounded-md border border-zinc-700 bg-zinc-900 px-3 py-2.5 text-sm text-white outline-none placeholder:text-zinc-600 focus:border-zinc-500 focus:ring-1 focus:ring-zinc-500"
                      required
                    />
                  </div>

                  <label className="flex items-center gap-2 text-xs text-zinc-400">
                    <input
                      type="checkbox"
                      checked={remember}
                      onChange={(e) => setRemember(e.target.checked)}
                      className="h-4 w-4 rounded border-zinc-600 bg-zinc-900 text-orange-600 focus:ring-orange-600"
                    />
                    Remember Email
                  </label>

                  <button
                    type="submit"
                    disabled={!isEmailValid}
                    className={cn(
                      'w-full rounded-md py-2.5 text-sm font-semibold transition',
                      isEmailValid
                        ? 'bg-zinc-100 text-zinc-900 hover:bg-white'
                        : 'cursor-not-allowed bg-zinc-800 text-zinc-500'
                    )}
                  >
                    Continue
                  </button>
                </>
              ) : (
                <>
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium text-zinc-300">Work Email</label>
                    <div className="rounded-md bg-zinc-800/50 px-3 py-2.5 text-sm text-zinc-300">{email}</div>
                  </div>

                  <div className="space-y-1.5">
                    <label htmlFor="password" className="text-xs font-medium text-zinc-300">
                      Password
                    </label>
                    <input
                      id="password"
                      type="password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="Enter your password"
                      autoFocus
                      className="w-full rounded-md border border-zinc-700 bg-zinc-900 px-3 py-2.5 text-sm text-white outline-none placeholder:text-zinc-600 focus:border-zinc-500 focus:ring-1 focus:ring-zinc-500"
                      required
                    />
                  </div>

                  <div className="flex items-center justify-between">
                    <button
                      type="button"
                      onClick={() => setStep('email')}
                      className="text-xs text-zinc-400 hover:text-zinc-200"
                    >
                      Use a different email
                    </button>
                  </div>

                  <button
                    type="submit"
                    disabled={!password || loading}
                    className={cn(
                      'w-full rounded-md py-2.5 text-sm font-semibold transition',
                      password && !loading
                        ? 'bg-blue-600 text-white hover:bg-blue-500'
                        : 'cursor-not-allowed bg-zinc-800 text-zinc-500'
                    )}
                  >
                    {loading ? 'Signing in...' : 'Sign in'}
                  </button>
                </>
              )}

              {error && (
                <p className="rounded-md bg-red-500/10 p-2 text-sm text-red-400">{error}</p>
              )}

              <a
                href="#"
                className="block text-left text-sm text-blue-500 hover:text-blue-400 hover:underline"
              >
                View Service Status
              </a>
            </form>
          </div>
        </div>

        <div className="flex items-center justify-between text-sm text-zinc-400">
          <div className="relative">
            <button
              type="button"
              className="flex items-center gap-1 hover:text-zinc-200 focus:outline-none"
              onClick={() => setHelpOpen((open) => !open)}
            >
              Need Help? <ChevronDown className={cn('h-3 w-3 transition', helpOpen && 'rotate-180')} />
            </button>
            {helpOpen && (
              <div className="absolute bottom-full left-0 z-20 mb-2 w-64 overflow-hidden rounded-lg border border-zinc-800 bg-zinc-900 py-1 shadow-xl">
                <button
                  type="button"
                  onClick={() => {
                    setEmail('admin@work.local')
                    setPassword('Password123!')
                    setStep('password')
                    setHelpOpen(false)
                  }}
                  className="block w-full px-4 py-2 text-left text-sm text-zinc-300 hover:bg-zinc-800 hover:text-white"
                >
                  Use demo account
                </button>
                <a
                  href="/"
                  className="block px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-800 hover:text-white"
                >
                  View Service Status
                </a>
                <a
                  href="mailto:it@alignedcardio.local"
                  className="block px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-800 hover:text-white"
                >
                  Contact IT
                </a>
              </div>
            )}
          </div>
          <div className="flex items-center gap-1">
            <Globe className="h-3 w-3" /> US
          </div>
        </div>
      </section>

      {/* Right column */}
      <section className="relative hidden w-1/2 overflow-hidden bg-zinc-950 lg:flex">
        <LoginVisual />
      </section>
    </main>
  )
}

function LoginFallback() {
  return (
    <main className="flex min-h-screen w-full items-center justify-center bg-black text-zinc-100">
      <div className="bg-[#232323] px-8 py-6 rounded-md">
        <p className="text-sm text-zinc-500">Loading...</p>
      </div>
    </main>
  )
}

export default function LoginPage() {
  return (
    <Suspense fallback={<LoginFallback />}>
      <LoginForm />
    </Suspense>
  )
}
