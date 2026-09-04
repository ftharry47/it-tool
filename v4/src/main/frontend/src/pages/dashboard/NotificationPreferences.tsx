import { useMsal } from '@azure/msal-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { fetchWithToken } from '../../api/client'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { Loading } from '../../components/ui/Loading'

function EventToggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (checked: boolean) => void }) {
  return (
    <label className="flex items-center justify-between rounded-md border border-border p-3">
      <span className="text-sm">{label}</span>
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="h-5 w-5"
      />
    </label>
  )
}

interface NotificationPreferenceResponse {
  inAppEnabled: boolean
  emailEnabled: boolean
  emailAddress: string
  digestMode: 'NONE' | 'HOURLY' | 'DAILY'
  notifyStatusChange: boolean
  notifyAssignment: boolean
  notifyComment: boolean
  notifyMention: boolean
}

const MODES: NotificationPreferenceResponse['digestMode'][] = ['NONE', 'HOURLY', 'DAILY']

export function NotificationPreferences() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()

  const [inAppEnabled, setInAppEnabled] = useState(true)
  const [emailEnabled, setEmailEnabled] = useState(true)
  const [emailAddress, setEmailAddress] = useState('')
  const [digestMode, setDigestMode] = useState<NotificationPreferenceResponse['digestMode']>('NONE')
  const [notifyStatusChange, setNotifyStatusChange] = useState(true)
  const [notifyAssignment, setNotifyAssignment] = useState(true)
  const [notifyComment, setNotifyComment] = useState(true)
  const [notifyMention, setNotifyMention] = useState(true)

  const query = useQuery<NotificationPreferenceResponse>({
    queryKey: ['notification-preferences'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/users/me/notification-preferences')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  useEffect(() => {
    if (query.data) {
      setInAppEnabled(query.data.inAppEnabled)
      setEmailEnabled(query.data.emailEnabled)
      setEmailAddress(query.data.emailAddress ?? '')
      setDigestMode(query.data.digestMode)
      setNotifyStatusChange(query.data.notifyStatusChange)
      setNotifyAssignment(query.data.notifyAssignment)
      setNotifyComment(query.data.notifyComment)
      setNotifyMention(query.data.notifyMention)
    }
  }, [query.data])

  const mutation = useMutation<NotificationPreferenceResponse, Error>({
    mutationFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/users/me/notification-preferences', {
        method: 'PUT',
        body: JSON.stringify({
          inAppEnabled,
          emailEnabled,
          emailAddress,
          digestMode,
          notifyStatusChange,
          notifyAssignment,
          notifyComment,
          notifyMention,
        }),
      })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notification-preferences'] }),
  })

  if (query.isLoading) return <Loading />
  if (query.error) return <ErrorFallback error={query.error} message="Could not load preferences." onRetry={() => query.refetch()} />

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-2xl space-y-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Notification Preferences</h1>
          <p className="text-sm text-muted-foreground">Choose how you receive notifications.</p>
        </div>

        <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <div className="space-y-6">
            <label className="flex items-center justify-between rounded-md border border-border p-4">
              <div>
                <p className="font-medium">In-app notifications</p>
                <p className="text-sm text-muted-foreground">Show notifications inside the application.</p>
              </div>
              <input
                type="checkbox"
                checked={inAppEnabled}
                onChange={(e) => setInAppEnabled(e.target.checked)}
                className="h-5 w-5"
              />
            </label>

            <label className="flex items-center justify-between rounded-md border border-border p-4">
              <div>
                <p className="font-medium">Email notifications</p>
                <p className="text-sm text-muted-foreground">Send notifications to your email address.</p>
              </div>
              <input
                type="checkbox"
                checked={emailEnabled}
                onChange={(e) => setEmailEnabled(e.target.checked)}
                className="h-5 w-5"
              />
            </label>

            <div>
              <p className="mb-2 text-sm font-medium">Notify me about</p>
              <div className="space-y-2">
                <EventToggle label="Status changes" checked={notifyStatusChange} onChange={setNotifyStatusChange} />
                <EventToggle label="Assignments" checked={notifyAssignment} onChange={setNotifyAssignment} />
                <EventToggle label="Comments" checked={notifyComment} onChange={setNotifyComment} />
                <EventToggle label="Mentions" checked={notifyMention} onChange={setNotifyMention} />
              </div>
            </div>

            {emailEnabled && (
              <div>
                <p className="mb-2 text-sm font-medium">Email address</p>
                <input
                  type="email"
                  value={emailAddress}
                  onChange={(e) => setEmailAddress(e.target.value)}
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                />
              </div>
            )}

            <div>
              <p className="mb-2 text-sm font-medium">Email digest mode</p>
              <select
                value={digestMode}
                onChange={(e) => setDigestMode(e.target.value as NotificationPreferenceResponse['digestMode'])}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              >
                {MODES.map((m) => <option key={m} value={m}>{m}</option>)}
              </select>
              <p className="mt-1 text-xs text-muted-foreground">NONE sends immediately; HOURLY/DAILY batches pending emails into digests.</p>
            </div>
          </div>

          <div className="mt-6 flex items-center gap-4">
            <button
              onClick={() => mutation.mutate()}
              disabled={mutation.isPending}
              className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
            >
              Save Preferences
            </button>
            {mutation.error && <p className="text-sm text-destructive">{mutation.error.message}</p>}
            {mutation.isSuccess && <p className="text-sm text-green-600">Saved.</p>}
          </div>
        </div>
      </div>
    </div>
  )
}
