import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MsalProvider } from '@azure/msal-react'
import { msalInstance } from './auth/authConfig'
import App from './App'
import './index.css'
import { ThemeProvider } from './components/theme/ThemeProvider'
import { registerServiceWorker } from './api/push'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60 * 1000,
      refetchInterval: 30 * 1000,
      refetchOnWindowFocus: true,
      retry: 1,
    },
  },
})

const root = ReactDOM.createRoot(document.getElementById('root')!)

;(async () => {
  try {
    await msalInstance.initialize()
  } catch (error) {
    console.error('[main] MSAL initialization failed:', error)
    return
  }

  msalInstance.handleRedirectPromise().catch((error) => {
    console.error('[main] handleRedirectPromise error:', error)
  })

  registerServiceWorker()

  root.render(
    <React.StrictMode>
      <MsalProvider instance={msalInstance}>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <ThemeProvider>
              <App />
            </ThemeProvider>
          </BrowserRouter>
        </QueryClientProvider>
      </MsalProvider>
    </React.StrictMode>
  )
})()
