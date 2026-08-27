import React, { createContext, useContext, useEffect, useState } from 'react'
import { useMsal, useIsAuthenticated } from '@azure/msal-react'
import { fetchWithToken } from '../api/client'

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
  const { instance, accounts } = useMsal()
  const isAuthenticated = useIsAuthenticated()
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!isAuthenticated || accounts.length === 0) {
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
      .catch(() => {
        if (!cancelled) setCurrentUser(null)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => { cancelled = true }
  }, [isAuthenticated, accounts, instance])

  const login = () => {
    instance.loginRedirect({ scopes: ['openid', 'profile', 'email', 'User.Read'] })
  }

  const logout = () => {
    instance.logoutRedirect({ postLogoutRedirectUri: '/' })
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
