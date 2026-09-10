import { useEffect, useMemo, useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import { EntityForm, type Field } from '../../components/ui/EntityForm'
import { formatStatusLabel } from '../../components/ui/StatusBadge'

const INCIDENT_STATUSES = ['NEW', 'IN_PROGRESS', 'ON_HOLD', 'WAITING_ON_CUSTOMER', 'RESOLVED', 'CLOSED', 'REOPENED']

interface Option {
  id: string
  name: string
}

interface IncidentEditFormProps {
  incidentId: string
  initial: {
    title: string
    description: string
    status: string
    priorityName: string | null
    categoryName: string | null
    assigneeName: string | null
    assigneeId: string | null
    locationId: string | null
  }
  users: Option[]
  priorities: Option[]
  categories: Option[]
  locations: Option[]
  onSaved: () => void
  /** Reports dirty state to the parent so it can confirm before discarding. */
  onDirtyChange?: (dirty: boolean) => void
}

function optionByName(options: Option[], name?: string | null): string {
  if (!name) return ''
  const match = options.find((o) => o.name === name)
  return match?.id ?? ''
}

export function IncidentEditForm({ incidentId, initial, users, priorities, categories, locations, onSaved, onDirtyChange }: IncidentEditFormProps) {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()
  const { currentUser } = useAuth()

  const isAdminOrSuperAdmin = (currentUser?.roles.includes('ADMIN') || currentUser?.roles.includes('SUPER_ADMIN')) ?? false
  const isPriorityLocked = (initial.assigneeId != null && initial.assigneeId !== '')
    || (initial.assigneeName != null && initial.assigneeName !== '')

  const initialValues = useMemo(
    () => ({
      title: initial.title,
      description: initial.description,
      status: initial.status,
      priorityId: optionByName(priorities, initial.priorityName),
      categoryId: optionByName(categories, initial.categoryName),
      assigneeId: initial.assigneeId ?? optionByName(users, initial.assigneeName),
      locationId: initial.locationId ?? '',
    }),
    [initial, priorities, categories, users]
  )

  const [values, setValues] = useState(initialValues)
  const isDirty = useMemo(() => JSON.stringify(values) !== JSON.stringify(initialValues), [values, initialValues])

  useEffect(() => {
    onDirtyChange?.(isDirty)
  }, [isDirty, onDirtyChange])

  // Note: useBlocker was removed — it requires a data router and this app
  // uses BrowserRouter/useRoutes, which crashed the form. beforeunload +
  // the Cancel button's confirm cover unsaved-changes protection.
  useEffect(() => {
    function handleBeforeUnload(e: BeforeUnloadEvent) {
      if (isDirty) {
        e.preventDefault()
        e.returnValue = ''
      }
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [isDirty])

  const mutation = useMutation<unknown, Error, typeof values>({
    mutationFn: async (payload) => {
      const body = {
        title: payload.title,
        description: payload.description,
        status: payload.status,
        priorityId: payload.priorityId || null,
        categoryId: payload.categoryId || null,
        assigneeId: payload.assigneeId || null,
        locationId: payload.locationId || null,
      }
      const res = await fetchWithToken(instance, account!, `/api/v1/incidents/${incidentId}`, {
        method: 'PATCH',
        body: JSON.stringify(body),
      })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['incident', incidentId] })
      queryClient.invalidateQueries({ queryKey: ['incidents'] })
      onSaved()
    },
  })

  const priorityRequired = !isPriorityLocked && values.assigneeId !== ''

  const fields: Field[] = [
    { name: 'title', label: 'Title', type: 'text', required: true },
    { name: 'description', label: 'Description', type: 'textarea' },
    {
      name: 'status',
      label: 'Status',
      type: 'select',
      required: true,
      options: INCIDENT_STATUSES.map((s) => ({ value: s, label: formatStatusLabel(s) })),
    },
    {
      name: 'priorityId',
      label: 'Priority',
      type: 'select',
      required: priorityRequired,
      disabled: !isAdminOrSuperAdmin || isPriorityLocked,
      options: priorities.map((p) => ({ value: p.id, label: p.name })),
    },
    {
      name: 'categoryId',
      label: 'Category',
      type: 'select',
      options: categories.map((c) => ({ value: c.id, label: c.name })),
    },
    {
      name: 'assigneeId',
      label: 'Assignee',
      type: 'select',
      options: users.map((u) => ({ value: u.id, label: u.name })),
    },
    {
      name: 'locationId',
      label: 'Location',
      type: 'select',
      options: locations.map((l) => ({ value: l.id, label: l.name })),
    },
  ]

  return (
    <div className="space-y-4">
      <EntityForm
        fields={fields}
        values={values}
        onChange={(name, value) => setValues((v) => ({ ...v, [name]: value }))}
        onSubmit={(e) => {
          e.preventDefault()
          mutation.mutate(values)
        }}
        submitLabel={mutation.isPending ? 'Saving…' : 'Save Changes'}
        pending={mutation.isPending}
      />
      {mutation.error && <p className="text-sm text-destructive">{mutation.error.message}</p>}
      {mutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
    </div>
  )
}
