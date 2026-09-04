import { Configuration, LogLevel, PublicClientApplication } from '@azure/msal-browser'

const clientId = import.meta.env.VITE_AZURE_CLIENT_ID
const tenantId = import.meta.env.VITE_AZURE_TENANT_ID
const baseUrl = import.meta.env.VITE_APP_BASE_URL

if (import.meta.env.PROD) {
  if (!clientId) throw new Error('VITE_AZURE_CLIENT_ID is required for production builds')
  if (!tenantId) throw new Error('VITE_AZURE_TENANT_ID is required for production builds')
}

const effectiveClientId = clientId || 'dummy-client-id'
const effectiveTenantId = tenantId || 'common'
const effectiveBaseUrl = (baseUrl || (typeof window !== 'undefined' ? window.location.origin : 'http://localhost:3000')).replace(/\/$/, '')

const msalConfig: Configuration = {
  auth: {
    clientId: effectiveClientId,
    authority: `https://login.microsoftonline.com/${effectiveTenantId}`,
    redirectUri: effectiveBaseUrl,
    postLogoutRedirectUri: `${effectiveBaseUrl}/`,
    navigateToLoginRequestUrl: false,
  },
  cache: {
    cacheLocation: 'localStorage',
    storeAuthStateInCookie: false,
  },
  system: {
    loggerOptions: {
      loggerCallback: (level, message) => {
        if (import.meta.env.DEV) {
          // eslint-disable-next-line no-console
          console.log(`[MSAL ${LogLevel[level]}] ${message}`)
        }
      },
      logLevel: LogLevel.Warning,
      piiLoggingEnabled: false,
    },
  },
}

export const msalInstance = new PublicClientApplication(msalConfig)

export const loginRequest = {
  scopes: ['openid', 'profile', 'email', 'offline_access'],
}
