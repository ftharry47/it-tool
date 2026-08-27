import { Configuration, LogLevel, PublicClientApplication } from '@azure/msal-browser'

const clientId = import.meta.env.VITE_AZURE_CLIENT_ID || 'dummy-client-id'
const tenantId = import.meta.env.VITE_AZURE_TENANT_ID || 'common'
const baseUrl = import.meta.env.VITE_APP_BASE_URL || 'http://localhost:3000'

const msalConfig: Configuration = {
  auth: {
    clientId,
    authority: `https://login.microsoftonline.com/${tenantId}`,
    redirectUri: baseUrl,
    postLogoutRedirectUri: `${baseUrl}/`,
    navigateToLoginRequestUrl: true,
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
  scopes: ['openid', 'profile', 'email', 'User.Read'],
}
