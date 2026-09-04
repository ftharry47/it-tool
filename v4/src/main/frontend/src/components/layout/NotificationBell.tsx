import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, Check, Settings } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'

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
  const ref = useRef<HTMLDivElement>(null)

  const isEndUser = currentUser?.roles.includes('END_USER') ?? false
  const isDashboardUser = currentUser?.roles.some((r) => ['AGENT', 'TEAM_LEAD', 'ADMIN', 'SUPER_ADMIN'].includes(r)) ?? false
  const preferencesPath = isEndUser && !isDashboardUser ? '/home/notifications' : '/dashboard/notifications'
  const viewBase = isEndUser && !isDashboardUser ? '/home' : '/dashboard'

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
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
  })

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

  const unreadCount = unreadQuery.data ?? 0

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
        <div className="absolute right-0 z-50 mt-2 w-80 origin-top-right rounded-xl border border-border bg-card p-2 shadow-lg">
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

          <div className="max-h-80 overflow-y-auto">
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
                {notification.body && <p className="text-xs text-muted-foreground line-clamp-2">{notification.body}</p>}
                <div className="flex items-center justify-between text-xs text-muted-foreground">
                  <span>{new Date(notification.createdAt).toLocaleString()}</span>
                  {notification.entityType && notification.entityId && (
                    <Link
                      to={`${viewBase}/${notification.entityType.toLowerCase()}s/${notification.entityId}`}
                      onClick={() => setOpen(false)}
                      className="text-primary hover:underline"
                    >
                      View
                    </Link>
                  )}
                </div>
              </div>
            ))}
          </div>

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
