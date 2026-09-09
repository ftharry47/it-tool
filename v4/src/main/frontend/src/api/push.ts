import { AccountInfo, IPublicClientApplication } from '@azure/msal-browser'
import { fetchWithToken } from './client'

const DISMISS_KEY = 'push-banner-dismissed'

export function isPushSupported(): boolean {
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
}

export function isBannerDismissed(): boolean {
  return localStorage.getItem(DISMISS_KEY) === '1'
}

export function dismissBanner(): void {
  localStorage.setItem(DISMISS_KEY, '1')
}

export async function registerServiceWorker(): Promise<ServiceWorkerRegistration | null> {
  if (!isPushSupported()) return null
  try {
    return await navigator.serviceWorker.register('/sw.js')
  } catch (e) {
    console.error('[push] service worker registration failed', e)
    return null
  }
}

function urlBase64ToUint8Array(base64: string): Uint8Array {
  const padding = '='.repeat((4 - (base64.length % 4)) % 4)
  const b64 = (base64 + padding).replace(/-/g, '+').replace(/_/g, '/')
  const raw = atob(b64)
  const arr = new Uint8Array(raw.length)
  for (let i = 0; i < raw.length; i++) arr[i] = raw.charCodeAt(i)
  return arr
}

export async function subscribeToPush(
  instance: IPublicClientApplication,
  account: AccountInfo | undefined
): Promise<boolean> {
  if (!isPushSupported()) return false

  const permission = await Notification.requestPermission()
  if (permission !== 'granted') return false

  const keyRes = await fetchWithToken(instance, account!, '/api/v1/push/vapid-public-key')
  if (!keyRes.ok) return false
  const { publicKey } = await keyRes.json()
  if (!publicKey) return false

  const reg = await registerServiceWorker()
  if (!reg) return false

  const sub = await reg.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(publicKey) as BufferSource,
  })

  const key = sub.getKey('p256dh')
  const auth = sub.getKey('auth')
  if (!key || !auth) return false

  const toB64 = (buf: ArrayBuffer) =>
    btoa(String.fromCharCode(...new Uint8Array(buf)))
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')

  const res = await fetchWithToken(instance, account!, '/api/v1/push/subscriptions', {
    method: 'POST',
    body: JSON.stringify({
      endpoint: sub.endpoint,
      p256dh: toB64(key),
      auth: toB64(auth),
      userAgent: navigator.userAgent.slice(0, 250),
    }),
  })
  return res.ok
}

export async function unsubscribeFromPush(
  instance: IPublicClientApplication,
  account: AccountInfo | undefined
): Promise<void> {
  const reg = await navigator.serviceWorker.getRegistration('/sw.js')
  const sub = await reg?.pushManager.getSubscription()
  if (sub) await sub.unsubscribe()

  const res = await fetchWithToken(instance, account!, '/api/v1/push/subscriptions')
  if (res.ok) {
    const subs: { id: string; endpoint: string }[] = await res.json()
    for (const s of subs) {
      if (!sub || s.endpoint === sub.endpoint) {
        await fetchWithToken(instance, account!, `/api/v1/push/subscriptions/${s.id}`, { method: 'DELETE' })
      }
    }
  }
}
