import { create } from 'zustand'

interface User {
  id: string
  email: string
  displayName: string
  role: 'END_USER' | 'AGENT' | 'TEAM_LEAD' | 'ADMIN' | 'SUPER_ADMIN'
  orgId: string
}

interface AuthState {
  user: User | null
  loaded: boolean
  setUser: (user: User | null) => void
  setLoaded: (loaded: boolean) => void
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  loaded: false,
  setUser: (user) => set({ user }),
  setLoaded: (loaded) => set({ loaded }),
}))
