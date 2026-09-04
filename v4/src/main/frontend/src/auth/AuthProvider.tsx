import React, { createContext, useContext, useEffect, useState } from 'react'
import { useMsal, useIsAuthenticated } from '@azure/msal-react'
import { fetchWithToken } from '../api/client'
import { loginRequest } from './authConfig'

// Same DEV-only gate as in api/client.ts. This keeps the local preview
// completely out of production builds that ship to Azure.
const LOCAL_AUTH_TOKEN =
  import.meta.env.DEV && import.meta.env.VITE_LOCAL_AUTH_TOKEN
    ? String(import.meta.env.VITE_LOCAL_AUTH_TOKEN)
    : undefined

export interface CurrentUser {
  id: string
  objectId: string
  email: string
  displayName: string
  jobTitle: string | null
  department: string | null
  roles: string[]
  isActive: boolean
  mfaEnabled: boolean
  managerId: string | null
}

interface AuthContextValue {
  currentUser: CurrentUser | null
  isAuthenticated: boolean
  loading: boolean
  login: () => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const { instance, accounts, inProgress } = useMsal()
  const msalAuthenticated = useIsAuthenticated()
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    console.log('[AuthProvider] inProgress:', inProgress, 'msalAuthenticated:', msalAuthenticated, 'accounts:', accounts.length, 'currentUser:', currentUser)
  }, [inProgress, msalAuthenticated, accounts, currentUser])

  // Local preview path: if a dev-only local token is set, call /api/auth/me
  // directly and skip MSAL's loginRedirect. The token is hard-gated on
  // import.meta.env.DEV, so this branch cannot run in a production build.
  useEffect(() => {
    if (!LOCAL_AUTH_TOKEN) {
      return
    }

    let cancelled = false
    setLoading(true)

    fetchWithToken(instance, accounts[0], '/api/auth/me')
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error(`HTTP ${res.status}`))))
      .then((data) => {
        if (!cancelled) setCurrentUser(data)
      })
      .catch(() => {
        if (!cancelled) setCurrentUser(null)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => { cancelled = true }
  }, [instance, accounts])

  // Normal MSAL path
  useEffect(() => {
    if (LOCAL_AUTH_TOKEN) {
      return
    }

    if (inProgress !== 'none') {
      setLoading(true)
      return
    }

    if (!msalAuthenticated || accounts.length === 0) {
      setCurrentUser(null)
      setLoading(false)
      return
    }

    let cancelled = false
    const account = accounts[0]

    fetchWithToken(instance, account, '/api/auth/me')
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error(`HTTP ${res.status}`))))
      .then((data) => {
        if (!cancelled) setCurrentUser(data)
      })
      .catch((err) => {
        console.error('AuthProvider: /api/auth/me failed', err)
        if (!cancelled) setCurrentUser(null)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => { cancelled = true }
  }, [msalAuthenticated, accounts, instance, inProgress])

  const isAuthenticated = msalAuthenticated || !!LOCAL_AUTH_TOKEN

  const login = () => {
    if (LOCAL_AUTH_TOKEN) {
      return
    }
    instance.loginRedirect(loginRequest)
  }

  const logout = () => {
    if (LOCAL_AUTH_TOKEN) {
      window.location.href = '/'
      return
    }
    instance.logoutRedirect()
  }

  return (
    <AuthContext.Provider value={{ currentUser, isAuthenticated, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
