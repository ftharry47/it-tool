import { AccountInfo, IPublicClientApplication } from '@azure/msal-browser'
import { loginRequest } from '../auth/authConfig'

// Local preview bypass. This is intentionally gated on import.meta.env.DEV:
// it cannot be active in a production build, so the local token is never
// embedded in the app.zip bundle that ships to Azure.
const LOCAL_AUTH_TOKEN =
  import.meta.env.DEV && import.meta.env.VITE_LOCAL_AUTH_TOKEN
    ? String(import.meta.env.VITE_LOCAL_AUTH_TOKEN)
    : undefined

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
    return fetch(url, { ...options, headers })
  }

  const tokenResponse = await instance.acquireTokenSilent({
    ...loginRequest,
    account,
  })

  const token = tokenResponse.idToken ?? tokenResponse.accessToken
  headers.set('Authorization', `Bearer ${token}`)
  return fetch(url, { ...options, headers })
}
