import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, Check, Settings } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { dismissBanner, isBannerDismissed, isPushSupported, subscribeToPush } from '../../api/push'
import { useAuth } from '../../auth/AuthProvider'
import { formatDateTime } from '../../lib/date'

interface Notification {
  id: string
  subject: string
  body: string
  entityType: string | null
  entityId: string | null
  read: boolean
  createdAt: string
}

export function NotificationBell() {
  const { instance, accounts } = useMsal()
  const { currentUser } = useAuth()
  const account = accounts[0]
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [showPushBanner, setShowPushBanner] = useState(false)
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    // Per-browser dismissal only (localStorage) — not synced to the backend.
    if (open && isPushSupported() && Notification.permission === 'default' && !isBannerDismissed()) {
      setShowPushBanner(true)
    }
  }, [open])

  const isEndUser = currentUser?.roles.includes('END_USER') ?? false
  const isDashboardUser = currentUser?.roles.some((r) => ['AGENT', 'TEAM_LEAD', 'ADMIN', 'SUPER_ADMIN'].includes(r)) ?? false
  const preferencesPath = isEndUser && !isDashboardUser ? '/home/notifications' : '/dashboard/notifications'
  const viewBase = isEndUser && !isDashboardUser ? '/home' : '/dashboard'

  // Explicit per-entityType link resolver — never guess a URL by pluralizing.
  // Returns null when the recipient's role has no accessible detail page; the
  // notification then expands in place instead of navigating to a 404.
  const resolveViewLink = (n: Notification): string | null => {
    if (!n.entityType || !n.entityId) return null
    switch (n.entityType) {
      case 'INCIDENT':
        return `${viewBase}/incidents/${n.entityId}`
      case 'SERVICE_REQUEST':
        return `${viewBase}/service-requests/${n.entityId}`
      case 'PROBLEM':
        return isDashboardUser ? `/dashboard/problems/${n.entityId}` : null
      case 'CHANGE':
        return isDashboardUser ? `/dashboard/changes/${n.entityId}` : null
      // LOCATION: only an admin list page exists, no detail route.
      // ISSUE: detail lives under /projects/:projectId/issues/:issueId and the
      // notification doesn't carry projectId — can't construct the URL.
      default:
        return null
    }
  }

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const unreadQuery = useQuery<number>({
    queryKey: ['notifications', 'unread-count'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/notifications/unread-count')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
    refetchInterval: 10_000,
    refetchOnWindowFocus: true,
  })

  // When the unread count changes, refresh the list too so an open dropdown
  // stays current without waiting for the next open.
  const unreadCount = unreadQuery.data ?? 0
  useEffect(() => {
    queryClient.invalidateQueries({ queryKey: ['notifications', 'list'] })
  }, [unreadCount, queryClient])

  const listQuery = useQuery<Notification[]>({
    queryKey: ['notifications', 'list'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/notifications?limit=10')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: open && !!account,
    refetchOnWindowFocus: true,
  })

  const markReadMutation = useMutation({
    mutationFn: async (id: string) => {
      const res = await fetchWithToken(instance, account, `/api/v1/notifications/${id}/read`, { method: 'POST' })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })

  const markAllReadMutation = useMutation({
    mutationFn: async () => {
      const res = await fetchWithToken(instance, account, '/api/v1/notifications/read-all', { method: 'POST' })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        className="relative rounded-md border border-border bg-background p-2 text-sm font-medium transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        aria-label="Notifications"
        aria-haspopup="menu"
        aria-expanded={open}
      >
        <Bell className="h-5 w-5" />
        {unreadCount > 0 && (
          <span className="absolute -right-1 -top-1 flex h-4 min-w-[1rem] items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-semibold text-destructive-foreground">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="animate-zoom-in-95 absolute right-0 z-50 mt-2 w-80 origin-top-right rounded-xl border border-border bg-card p-2 shadow-lg">
          <div className="mb-2 flex items-center justify-between border-b border-border p-2">
            <span className="text-sm font-semibold">Notifications</span>
            {unreadCount > 0 && (
              <button
                onClick={() => markAllReadMutation.mutate()}
                disabled={markAllReadMutation.isPending}
                className="flex items-center gap-1 text-xs text-primary hover:underline disabled:opacity-50"
              >
                <Check className="h-3 w-3" />
                Mark all read
              </button>
            )}
          </div>

          <div className="max-h-80 overflow-y-auto scrollbar-themed">
            {listQuery.isLoading && <p className="p-3 text-sm text-muted-foreground">Loading…</p>}
            {listQuery.error && <p className="p-3 text-sm text-destructive">Could not load notifications.</p>}
            {!listQuery.isLoading && !listQuery.error && (listQuery.data?.length ?? 0) === 0 && (
              <p className="p-3 text-sm text-muted-foreground">No notifications.</p>
            )}
            {listQuery.data?.map((notification) => (
              <div
                key={notification.id}
                className={`flex flex-col gap-1 rounded-md p-2 text-sm ${notification.read ? 'text-muted-foreground' : 'bg-muted/50 font-medium text-foreground'}`}
              >
                <div className="flex items-start justify-between gap-2">
                  <span className="flex-1">{notification.subject}</span>
                  {!notification.read && (
                    <button
                      onClick={() => markReadMutation.mutate(notification.id)}
                      disabled={markReadMutation.isPending}
                      className="shrink-0 rounded p-1 hover:bg-muted disabled:opacity-50"
                      aria-label="Mark as read"
                    >
                      <Check className="h-3 w-3" />
                    </button>
                  )}
                </div>
                {notification.body && (
                  <p className={`text-xs text-muted-foreground ${expandedId === notification.id ? '' : 'line-clamp-2'}`}>
                    {notification.body}
                  </p>
                )}
                <div className="flex items-center justify-between text-xs text-muted-foreground">
                  <span>{formatDateTime(notification.createdAt)}</span>
                  {(() => {
                    const link = resolveViewLink(notification)
                    if (link) {
                      return (
                        <Link
                          to={link}
                          onClick={() => setOpen(false)}
                          className="text-primary hover:underline"
                        >
                          View
                        </Link>
                      )
                    }
                    if (notification.body) {
                      return (
                        <button
                          onClick={() => setExpandedId(expandedId === notification.id ? null : notification.id)}
                          className="text-primary hover:underline"
                        >
                          {expandedId === notification.id ? 'Show less' : 'View'}
                        </button>
                      )
                    }
                    return null
                  })()}
                </div>
              </div>
            ))}
          </div>

          {showPushBanner && (
            <div className="mt-2 rounded-md border border-border bg-muted/40 p-3 text-sm">
              <p className="mb-2">Get notified even when this tab is closed.</p>
              <div className="flex gap-2">
                <button
                  onClick={async () => {
                    await subscribeToPush(instance, account)
                    setShowPushBanner(false)
                    dismissBanner()
                  }}
                  className="rounded-md bg-primary px-3 py-1 text-xs font-medium text-primary-foreground hover:bg-primary/90"
                >
                  Enable
                </button>
                <button
                  onClick={() => {
                    dismissBanner()
                    setShowPushBanner(false)
                  }}
                  className="rounded-md px-3 py-1 text-xs text-muted-foreground hover:bg-muted"
                >
                  Not now
                </button>
              </div>
            </div>
          )}

          <Link
            to={preferencesPath}
            onClick={() => setOpen(false)}
            className="mt-2 flex w-full items-center gap-2 rounded-md border-t border-border px-3 py-2 text-sm transition hover:bg-muted"
          >
            <Settings className="h-4 w-4" />
            Notification preferences
          </Link>
        </div>
      )}
    </div>
  )
}
