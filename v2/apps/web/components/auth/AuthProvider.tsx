'use client'

import * as React from 'react'
import { setToken, clearToken, getToken, api } from '@/lib/api'

type User = {
  id: string
  email: string
  name: string
  role: string
  managerId: string | null
}

type AuthContextValue = {
  user: User | null
  token: string
  isLoading: boolean
  login: (email: string) => Promise<void>
  logout: () => void
}

const AuthContext = React.createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = React.useState<User | null>(null)
  const [token, setTokenState] = React.useState('')
  const [isLoading, setIsLoading] = React.useState(true)

  React.useEffect(() => {
    const t = getToken()
    if (t) {
      setToken(t)
      api('/auth/me')
        .then((u) => setUser(u))
        .catch(() => clearToken())
        .finally(() => setIsLoading(false))
    } else {
      setIsLoading(false)
    }
  }, [])

  const login = React.useCallback(async (email: string) => {
    const res = await api('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email }),
    })
    setToken(res.accessToken)
    setTokenState(res.accessToken)
    setUser(res.user)
  }, [])

  const logout = React.useCallback(() => {
    clearToken()
    setTokenState('')
    setUser(null)
  }, [])

  return <AuthContext.Provider value={{ user, token, isLoading, login, logout }}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = React.useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
