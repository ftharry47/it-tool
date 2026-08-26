const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:3001'

export function getToken() {
  if (typeof window === 'undefined') return ''
  return localStorage.getItem('dev-it-token') || ''
}

export function setToken(token: string) {
  if (typeof window !== 'undefined') {
    localStorage.setItem('dev-it-token', token)
  }
}

export function clearToken() {
  if (typeof window !== 'undefined') {
    localStorage.removeItem('dev-it-token')
    localStorage.removeItem('dev-it-user')
  }
}

export async function api(path: string, options: RequestInit = {}) {
  const token = getToken()
  const res = await fetch(`${API_BASE}/api${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })
  if (res.status === 401) {
    clearToken()
    throw new Error('Unauthorized')
  }
  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || `HTTP ${res.status}`)
  }
  return res.json()
}
