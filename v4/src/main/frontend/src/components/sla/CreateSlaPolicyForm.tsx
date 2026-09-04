import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AccountInfo, IPublicClientApplication } from '@azure/msal-browser'
import { fetchWithToken } from '../../api/client'

interface PriorityOption {
  id: string
  name: string
}

interface CalendarOption {
  id: string
  name: string
}

interface CreateSlaPolicyFormProps {
  instance: IPublicClientApplication
  account: AccountInfo | undefined
  priorities: PriorityOption[]
  calendars: CalendarOption[]
  onCreated: () => void
}

export function CreateSlaPolicyForm({
  instance,
  account,
  priorities,
  calendars,
  onCreated,
}: CreateSlaPolicyFormProps) {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [appliesTo, setAppliesTo] = useState<'INCIDENT' | 'REQUEST'>('INCIDENT')
  const [priorityFilter, setPriorityFilter] = useState('')
  const [responseTargetMinutes, setResponseTargetMinutes] = useState('')
  const [resolutionTargetMinutes, setResolutionTargetMinutes] = useState('')
  const [businessHoursCalendarId, setBusinessHoursCalendarId] = useState('')

  const createPolicy = useMutation({
    mutationFn: async () => {
      const body = {
        name,
        appliesTo,
        priorityFilter: priorityFilter || null,
        responseTargetMinutes: Number(responseTargetMinutes),
        resolutionTargetMinutes: Number(resolutionTargetMinutes),
        businessHoursCalendarId: businessHoursCalendarId || null,
      }
      const res = await fetchWithToken(instance, account, '/api/v1/sla-policies', {
        method: 'POST',
        body: JSON.stringify(body),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sla-policies'] })
      setName('')
      setAppliesTo('INCIDENT')
      setPriorityFilter('')
      setResponseTargetMinutes('')
      setResolutionTargetMinutes('')
      setBusinessHoursCalendarId('')
      onCreated()
    },
  })

  const canSubmit =
    !!account &&
    name.trim() !== '' &&
    responseTargetMinutes !== '' &&
    resolutionTargetMinutes !== '' &&
    !createPolicy.isPending

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        if (canSubmit) createPolicy.mutate()
      }}
      className="space-y-3 rounded-xl border border-border bg-card p-4 shadow-sm"
    >
      <h3 className="text-sm font-medium text-muted-foreground">New SLA Policy</h3>
      {createPolicy.error && (
        <p className="text-sm text-red-600">Could not create policy: {createPolicy.error.message}</p>
      )}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <div className="space-y-1">
          <label className="text-xs font-medium text-muted-foreground">Name</label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            placeholder="e.g. P1 Incident Response"
            required
          />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium text-muted-foreground">Applies To</label>
          <select
            value={appliesTo}
            onChange={(e) => setAppliesTo(e.target.value as 'INCIDENT' | 'REQUEST')}
            className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
          >
            <option value="INCIDENT">Incident</option>
            <option value="REQUEST">Request</option>
          </select>
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium text-muted-foreground">Priority Filter</label>
          <select
            value={priorityFilter}
            onChange={(e) => setPriorityFilter(e.target.value)}
            className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
          >
            <option value="">All priorities</option>
            {priorities.map((p) => (
              <option key={p.id} value={p.name}>
                {p.name}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium text-muted-foreground">Response Target (min)</label>
          <input
            type="number"
            value={responseTargetMinutes}
            onChange={(e) => setResponseTargetMinutes(e.target.value)}
            className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            min={1}
            required
          />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium text-muted-foreground">Resolution Target (min)</label>
          <input
            type="number"
            value={resolutionTargetMinutes}
            onChange={(e) => setResolutionTargetMinutes(e.target.value)}
            className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            min={1}
            required
          />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium text-muted-foreground">Business Calendar</label>
          <select
            value={businessHoursCalendarId}
            onChange={(e) => setBusinessHoursCalendarId(e.target.value)}
            className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
          >
            <option value="">None</option>
            {calendars.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
      </div>
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={!canSubmit}
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-60"
        >
          {createPolicy.isPending ? 'Creating…' : 'Create Policy'}
        </button>
        <button
          type="button"
          onClick={onCreated}
          className="rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-muted"
        >
          Cancel
        </button>
      </div>
    </form>
  )
}
