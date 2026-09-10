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

// Fired on window when an API call returns 401 and a forced silent token
// refresh cannot recover — AuthProvider can redirect to re-authenticate.
export const AUTH_UNAUTHORIZED_EVENT = 'auth:unauthorized'

function notifyAuthRefresh(res: Response, url: string) {
  // Skip /api/auth/me itself to avoid a refresh loop.
  if (res.status === 403 && !url.includes('/api/auth/me')) {
    window.dispatchEvent(new CustomEvent(AUTH_REFRESH_EVENT))
  }
}

function notifyUnauthorized(res: Response, url: string) {
  if (res.status === 401 && !url.includes('/api/auth/me')) {
    window.dispatchEvent(new CustomEvent(AUTH_UNAUTHORIZED_EVENT))
  }
}

async function fetchOnce(
  url: string,
  options: RequestInit,
  headers: Headers
): Promise<Response> {
  const res = await fetch(url, { ...options, headers })
  notifyAuthRefresh(res, url)
  return res
}

async function tryToken(instance: IPublicClientApplication, account: AccountInfo | undefined, forceRefresh = false) {
  const tokenResponse = await instance.acquireTokenSilent({
    ...loginRequest,
    account,
    forceRefresh,
  } as any)
  return tokenResponse.idToken
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
    const res = await fetchOnce(url, options, headers)
    return res
  }

  const token = await tryToken(instance, account)
  if (!token) {
    window.dispatchEvent(new CustomEvent(AUTH_UNAUTHORIZED_EVENT))
    throw new Error('No idToken available from MSAL')
  }

  headers.set('Authorization', `Bearer ${token}`)
  const res = await fetchOnce(url, options, headers)

  // On 401, force MSAL to bypass its cache and fetch a fresh token once.
  if (res.status === 401) {
    try {
      const freshToken = await tryToken(instance, account, true)
      if (!freshToken) {
        notifyUnauthorized(res, url)
        throw new Error('No idToken available after forced refresh')
      }
      headers.set('Authorization', `Bearer ${freshToken}`)
      const retry = await fetchOnce(url, options, headers)
      if (retry.status === 401) {
        notifyUnauthorized(retry, url)
      }
      return retry
    } catch (err) {
      notifyUnauthorized(res, url)
      throw err
    }
  }

  return res
}
