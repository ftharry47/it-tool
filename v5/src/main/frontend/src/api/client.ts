import { AccountInfo, IPublicClientApplication } from '@azure/msal-browser'
import { loginRequest } from '../auth/authConfig'

// Local preview bypass. This is intentionally gated on import.meta.env.DEV:
// it cannot be active in a production build, so the local token is never
// embedded in the app.zip bundle that ships to Azure.
const LOCAL_AUTH_TOKEN =
  import.meta.env.DEV && import.meta.env.VITE_LOCAL_AUTH_TOKEN
    ? String(import.meta.env.VITE_LOCAL_AUTH_TOKEN)
    : undefined

// Fired on window when any API call returns 403 — AuthProvider listens and
// refetches /api/auth/me so a role change corrects the UI without a reload.
export const AUTH_REFRESH_EVENT = 'auth:refresh-current-user'

function notifyAuthRefresh(res: Response, url: string) {
  // Skip /api/auth/me itself to avoid a refresh loop.
  if (res.status === 403 && !url.includes('/api/auth/me')) {
    window.dispatchEvent(new CustomEvent(AUTH_REFRESH_EVENT))
  }
}

export async function fetchWithToken(
  instance: IPublicClientApplication,
  account: AccountInfo | undefined,
  url: string,
  options: RequestInit = {}
): Promise<Response> {
  const headers = new Headers(options.headers)
  if (options.body && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }

  if (LOCAL_AUTH_TOKEN) {
    headers.set('Authorization', `Bearer ${LOCAL_AUTH_TOKEN}`)
    const res = await fetch(url, { ...options, headers })
    notifyAuthRefresh(res, url)
    return res
  }

  const tokenResponse = await instance.acquireTokenSilent({
    ...loginRequest,
    account,
  })

  const token = tokenResponse.idToken ?? tokenResponse.accessToken
  headers.set('Authorization', `Bearer ${token}`)
  const res = await fetch(url, { ...options, headers })
  notifyAuthRefresh(res, url)
  return res
}
