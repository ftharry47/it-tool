import { useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Plus, Trash2, X } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { Loading } from '../../components/ui/Loading'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { ToastStack, type ToastItem } from '../../components/ui/Toast'
import { useSmartBack } from '../../lib/useSmartBack'

interface BusinessCalendar {
  id: string
  name: string
  timezone: string
  workingHours: string
  holidays: string
}

const DEFAULT_WORKING_HOURS = JSON.stringify(
  {
    monday: { start: '09:00', end: '17:00' },
    tuesday: { start: '09:00', end: '17:00' },
    wednesday: { start: '09:00', end: '17:00' },
    thursday: { start: '09:00', end: '17:00' },
    friday: { start: '09:00', end: '17:00' },
    saturday: null,
    sunday: null,
  },
  null,
  2
)

const DEFAULT_HOLIDAYS = '[]'

const EMPTY_FORM = {
  id: '',
  name: '',
  timezone: 'UTC',
  workingHours: DEFAULT_WORKING_HOURS,
  holidays: DEFAULT_HOLIDAYS,
}

export function BusinessCalendars() {
  const smartBack = useSmartBack('/admin')
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()
  const [form, setForm] = useState<BusinessCalendar>(EMPTY_FORM)
  const [original, setOriginal] = useState<BusinessCalendar | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState<BusinessCalendar | null>(null)
  const [confirmDiscard, setConfirmDiscard] = useState<(() => void) | null>(null)
  const [toasts, setToasts] = useState<ToastItem[]>([])

  const pushToast = (type: ToastItem['type'], message: string) => {
    setToasts((prev) => [...prev, { id: crypto.randomUUID(), type, message }])
  }
  const dismissToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }

  const query = useQuery<BusinessCalendar[]>({
    queryKey: ['business-calendars'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/business-calendars')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const saveMutation = useMutation<BusinessCalendar, Error, BusinessCalendar>({
    mutationFn: async (calendar) => {
      const body = {
        name: calendar.name,
        timezone: calendar.timezone,
        workingHours: calendar.workingHours,
        holidays: calendar.holidays,
      }
      const url = calendar.id ? `/api/v1/business-calendars/${calendar.id}` : '/api/v1/business-calendars'
      const res = await fetchWithToken(instance, account!, url, {
        method: calendar.id ? 'PUT' : 'POST',
        body: JSON.stringify(body),
      })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: (_data, calendar) => {
      queryClient.invalidateQueries({ queryKey: ['business-calendars'] })
      setForm(EMPTY_FORM)
      setOriginal(null)
      setError(null)
      pushToast('success', calendar.id ? 'Calendar updated' : 'Calendar created')
    },
    onError: (err) => {
      console.error('Calendar save failed:', err)
      setError('Could not save the calendar. Please try again or contact IT support.')
      pushToast('error', 'Could not save the calendar.')
    },
  })

  const deleteMutation = useMutation<void, Error, string>({
    mutationFn: async (id) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/business-calendars/${id}`, { method: 'DELETE' })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['business-calendars'] })
      if (form.id) {
        setForm(EMPTY_FORM)
        setOriginal(null)
      }
      setDeleting(null)
      pushToast('success', 'Calendar deleted')
    },
    onError: (err) => {
      console.error('Calendar delete failed:', err)
      setDeleting(null)
      pushToast('error', 'Could not delete the calendar. It may be referenced by an SLA policy.')
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    saveMutation.mutate(form)
  }

  const isFormDirty = (): boolean => {
    const baseline = original ?? EMPTY_FORM
    return (
      form.name !== baseline.name ||
      form.timezone !== baseline.timezone ||
      form.workingHours !== baseline.workingHours ||
      form.holidays !== baseline.holidays
    )
  }

  const guardDiscard = (action: () => void) => {
    if (isFormDirty()) setConfirmDiscard(() => action)
    else action()
  }

  const handleEdit = (calendar: BusinessCalendar) => {
    setForm(calendar)
    setOriginal(calendar)
    setError(null)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const handleCancel = () => {
    setForm(EMPTY_FORM)
    setOriginal(null)
    setError(null)
  }

  if (query.isLoading) return <Loading />
  if (query.error) return <ErrorFallback error={query.error} message="Could not load calendars." onRetry={() => query.refetch()} />

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <ToastStack toasts={toasts} onDismiss={dismissToast} />
      <ConfirmDialog
        open={deleting !== null}
        title={`Delete calendar "${deleting?.name}"?`}
        description="This cannot be undone. Calendars referenced by SLA policies cannot be deleted."
        confirmLabel="Delete"
        destructive
        onConfirm={() => deleting && deleteMutation.mutate(deleting.id)}
        onCancel={() => setDeleting(null)}
      />
      <ConfirmDialog
        open={confirmDiscard !== null}
        title="Discard unsaved changes?"
        description="You have unsaved changes to this calendar that will be lost."
        confirmLabel="Discard"
        destructive
        onConfirm={() => {
          const action = confirmDiscard
          setConfirmDiscard(null)
          action?.()
        }}
        onCancel={() => setConfirmDiscard(null)}
      />
      <div className="mx-auto max-w-4xl space-y-6">
        <div>
          <button
            onClick={smartBack}
            className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </button>
          <h1 className="text-2xl font-semibold tracking-tight">Business Calendars</h1>
          <p className="text-sm text-muted-foreground">Manage working hours, timezones, and holidays used by SLA policies.</p>
        </div>

        <form onSubmit={handleSubmit} className="rounded-xl border border-border bg-card p-6 shadow-sm space-y-4">
          <h2 className="text-lg font-semibold tracking-tight">
            {form.id ? 'Edit Calendar' : 'Add Calendar'}
          </h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="space-y-1">
              <label className="text-sm font-medium">Name</label>
              <input
                required
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                placeholder="e.g. Default Business Hours"
              />
            </div>
            <div className="space-y-1">
              <label className="text-sm font-medium">Timezone</label>
              <input
                required
                value={form.timezone}
                onChange={(e) => setForm({ ...form, timezone: e.target.value })}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                placeholder="e.g. America/New_York"
              />
            </div>
          </div>
          <div className="space-y-1">
            <label className="text-sm font-medium">Working Hours (JSON)</label>
            <textarea
              required
              rows={6}
              value={form.workingHours}
              onChange={(e) => setForm({ ...form, workingHours: e.target.value })}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm font-mono"
            />
          </div>
          <div className="space-y-1">
            <label className="text-sm font-medium">Holidays (JSON array)</label>
            <textarea
              required
              rows={4}
              value={form.holidays}
              onChange={(e) => setForm({ ...form, holidays: e.target.value })}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm font-mono"
            />
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
          <div className="flex gap-2">
            <button
              type="submit"
              disabled={saveMutation.isPending}
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
            >
              <Plus className="h-4 w-4" />
              {form.id ? 'Update Calendar' : 'Add Calendar'}
            </button>
            {form.id && (
              <button
                type="button"
                onClick={() => guardDiscard(handleCancel)}
                className="inline-flex items-center gap-2 rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-muted"
              >
                <X className="h-4 w-4" />
                Cancel
              </button>
            )}
          </div>
        </form>

        <div className="rounded-xl border border-border bg-card shadow-sm">
          <div className="border-b border-border px-6 py-4">
            <h2 className="text-lg font-semibold tracking-tight">Calendars</h2>
          </div>
          <div className="divide-y divide-border">
            {(query.data ?? []).length === 0 && (
              <p className="px-6 py-4 text-sm text-muted-foreground">No business calendars configured.</p>
            )}
            {(query.data ?? []).map((calendar) => (
              <div key={calendar.id} className="flex items-start justify-between gap-4 px-6 py-4">
                <div>
                  <p className="font-medium">{calendar.name}</p>
                  <p className="text-sm text-muted-foreground">{calendar.timezone}</p>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => guardDiscard(() => handleEdit(calendar))}
                    className="rounded-md border border-border px-3 py-1.5 text-sm font-medium transition hover:bg-muted"
                  >
                    Edit
                  </button>
                  <button
                    onClick={() => setDeleting(calendar)}
                    disabled={deleteMutation.isPending}
                    className="inline-flex items-center gap-1 rounded-md bg-destructive px-3 py-1.5 text-sm font-medium text-destructive-foreground transition hover:bg-destructive/90 disabled:opacity-50"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                    Delete
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
