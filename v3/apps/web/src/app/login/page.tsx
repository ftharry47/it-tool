'use client'

import { useState, FormEvent } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { useAuth } from '@/lib/api'
import { Button } from '@/components/button'

export default function LoginPage() {
  const router = useRouter()
  const { login } = useAuth()
  const [email, setEmail] = useState('admin@work.local')
  const [password, setPassword] = useState('Password123!')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      await login(email, password)
      router.push('/dashboard/issues')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center p-6">
      <form onSubmit={submit} className="w-full max-w-md space-y-4 rounded-xl border p-8">
        <h1 className="text-2xl font-semibold">Sign in</h1>
        <p className="rounded bg-muted p-2 text-xs text-muted-foreground">
          Demo admin: <span className="font-medium text-foreground">admin@work.local</span> / <span className="font-medium text-foreground">Password123!</span>
        </p>
        {error && <p className="rounded bg-destructive/10 p-2 text-sm text-destructive">{error}</p>}
        <div className="space-y-2">
          <label className="text-sm text-muted-foreground">Email</label>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full rounded-md border bg-background px-3 py-2 text-sm outline-none focus:ring-1 focus:ring-ring"
          />
        </div>
        <div className="space-y-2">
          <label className="text-sm text-muted-foreground">Password</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full rounded-md border bg-background px-3 py-2 text-sm outline-none focus:ring-1 focus:ring-ring"
          />
        </div>
        <Button type="submit" loading={loading} className="w-full">
          Sign in
        </Button>
        <div className="text-center">
          <Link href="/" className="text-sm text-muted-foreground hover:text-foreground">
            &larr; Back to home
          </Link>
        </div>
      </form>
    </main>
  )
}
