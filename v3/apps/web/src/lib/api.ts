import { useState, useEffect } from 'react'

export const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:3003'

export function api(path: string) {
  return `${API_URL}${path}`
}

export interface AuthUser {
  id: string
  email: string
  name: string
  roles: string[]
  permissions: string[]
  access_token: string
}

export function useAuth() {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    const raw = typeof window !== 'undefined' ? localStorage.getItem('work:auth') : null
    if (raw) {
      try {
        setUser(JSON.parse(raw))
      } catch {}
    }
    setLoaded(true)
  }, [])

  const login = async (email: string, password: string) => {
    const res = await fetch(api('/api/auth/login'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    })
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || 'Login failed')
    }
    const data = await res.json()
    const user: AuthUser = {
      ...data.user,
      access_token: data.access_token,
    }
    localStorage.setItem('work:auth', JSON.stringify(user))
    setUser(user)
    return user
  }

  const logout = () => {
    localStorage.removeItem('work:auth')
    setUser(null)
  }

  const token = user?.access_token

  const authed = (init?: RequestInit): RequestInit => ({
    ...init,
    headers: {
      ...init?.headers,
      Authorization: token ? `Bearer ${token}` : '',
      'Content-Type': 'application/json',
    },
  })

  return { user, loaded, login, logout, token, authed }
}
