import { AccountInfo, IPublicClientApplication } from '@azure/msal-browser'
import { loginRequest } from '../auth/authConfig'

export async function fetchWithToken(
  instance: IPublicClientApplication,
  account: AccountInfo,
  url: string,
  options: RequestInit = {}
): Promise<Response> {
  const tokenResponse = await instance.acquireTokenSilent({
    ...loginRequest,
    account,
  })

  const headers = new Headers(options.headers)
  headers.set('Authorization', `Bearer ${tokenResponse.accessToken}`)
  if (options.body) {
    headers.set('Content-Type', 'application/json')
  }

  return fetch(url, { ...options, headers })
}
