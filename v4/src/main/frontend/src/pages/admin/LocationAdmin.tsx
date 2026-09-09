import { useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Loader2, MapPin, Pencil, Plus, Trash2 } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { FormDrawer } from '../../components/ui/FormDrawer'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { UserCombobox } from '../../components/ui/UserCombobox'
import { ToastStack, type ToastItem } from '../../components/ui/Toast'

interface Location {
  id: string
  name: string
  address: string | null
  approvalManagerUserId: string | null
  approvalManagerName: string | null
  createdAt: string
  updatedAt: string
}

interface User {
  id: string
  email: string
  displayName: string
  roles: string[]
}

interface LocationFormState {
  name: string
  address: string
  approvalManagerUserId: string
}

const EMPTY_FORM: LocationFormState = { name: '', address: '', approvalManagerUserId: '' }

export function LocationAdmin() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<Location | null>(null)
  const [form, setForm] = useState<LocationFormState>(EMPTY_FORM)
  const [formError, setFormError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState<Location | null>(null)
  const [toasts, setToasts] = useState<ToastItem[]>([])

  const pushToast = (type: ToastItem['type'], message: string) => {
    setToasts((prev) => [...prev, { id: crypto.randomUUID(), type, message }])
  }
  const dismissToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }

  const locationsQuery = useQuery<Location[]>({
    queryKey: ['locations'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/locations')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const usersQuery = useQuery<User[]>({
    queryKey: ['admin-users'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/users')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const allUsers = usersQuery.data ?? []

  const saveMutation = useMutation<Location, Error>({
    mutationFn: async () => {
      const body = JSON.stringify({
        name: form.name,
        address: form.address || null,
        approvalManagerUserId: form.approvalManagerUserId || null,
      })
      const res = await fetchWithToken(
        instance,
        account!,
        editing ? `/api/v1/locations/${editing.id}` : '/api/v1/locations',
        { method: editing ? 'PATCH' : 'POST', body }
      )
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['locations'] })
      setDrawerOpen(false)
      setEditing(null)
      setForm(EMPTY_FORM)
      setFormError(null)
      pushToast('success', editing ? 'Location updated successfully' : 'Location created successfully')
    },
    onError: (error) => {
      console.error('Location save failed:', error)
      setFormError('Could not save the location. Please try again or contact IT support.')
    },
  })

  const deleteMutation = useMutation<void, Error, string>({
    mutationFn: async (id) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/locations/${id}`, {
        method: 'DELETE',
      })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['locations'] })
      setFormError(null)
      setDeleting(null)
      pushToast('success', 'Location deleted')
    },
    onError: (error) => {
      console.error('Location delete failed:', error)
      setDeleting(null)
      pushToast('error', 'Could not delete the location. It may be in use by existing tickets.')
    },
  })

  const openCreate = () => {
    setEditing(null)
    setForm(EMPTY_FORM)
    setFormError(null)
    setDrawerOpen(true)
  }

  const openEdit = (location: Location) => {
    setEditing(location)
    setForm({
      name: location.name,
      address: location.address ?? '',
      approvalManagerUserId: location.approvalManagerUserId ?? '',
    })
    setFormError(null)
    setDrawerOpen(true)
  }

  if (locationsQuery.isLoading) return <Loading />
  if (locationsQuery.error) return <ErrorFallback error={locationsQuery.error} message="Could not load locations." onRetry={() => locationsQuery.refetch()} />

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <ToastStack toasts={toasts} onDismiss={dismissToast} />
      <ConfirmDialog
        open={deleting !== null}
        title={`Delete location "${deleting?.name}"?`}
        description="This cannot be undone. Locations in use by existing tickets cannot be deleted."
        confirmLabel="Delete"
        destructive
        onConfirm={() => deleting && deleteMutation.mutate(deleting.id)}
        onCancel={() => setDeleting(null)}
      />
      <div className="mx-auto max-w-5xl space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold tracking-tight">Locations</h1>
          <button
            onClick={openCreate}
            className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <Plus className="h-4 w-4" />
            New Location
          </button>
        </div>

        {formError && !drawerOpen && <p className="text-sm text-destructive">{formError}</p>}

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <div className="overflow-x-auto scrollbar-themed">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-muted-foreground">
                  <th className="pb-2 pr-4 font-medium">Name</th>
                  <th className="pb-2 pr-4 font-medium">Address</th>
                  <th className="pb-2 pr-4 font-medium">Approval Manager</th>
                  <th className="pb-2 pr-4 font-medium">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {(locationsQuery.data ?? []).map((loc) => (
                  <tr key={loc.id}>
                    <td className="py-3 pr-4 font-medium">
                      <span className="inline-flex items-center gap-2">
                        <MapPin className="h-4 w-4 text-muted-foreground" />
                        {loc.name}
                      </span>
                    </td>
                    <td className="py-3 pr-4">{loc.address ?? '—'}</td>
                    <td className="py-3 pr-4">{loc.approvalManagerName ?? '—'}</td>
                    <td className="py-3 pr-4">
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => openEdit(loc)}
                          className="inline-flex items-center gap-1 rounded-md border border-border px-2 py-1 text-xs font-medium transition hover:bg-muted"
                        >
                          <Pencil className="h-3 w-3" />
                          Edit
                        </button>
                        <button
                          onClick={() => setDeleting(loc)}
                          disabled={deleteMutation.isPending}
                          className="inline-flex items-center gap-1 rounded-md border border-border px-2 py-1 text-xs font-medium text-destructive transition hover:bg-muted disabled:opacity-50"
                        >
                          <Trash2 className="h-3 w-3" />
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                {locationsQuery.data?.length === 0 && (
                  <tr>
                    <td colSpan={4} className="py-8 text-center text-muted-foreground">
                      No locations yet.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      </div>

      <FormDrawer
        open={drawerOpen}
        title={editing ? 'Edit Location' : 'New Location'}
        dirty={JSON.stringify(form) !== JSON.stringify(editing ? {
          name: editing.name,
          address: editing.address ?? '',
          approvalManagerUserId: editing.approvalManagerUserId ?? '',
        } : EMPTY_FORM)}
        onClose={() => {
          setDrawerOpen(false)
          setEditing(null)
          setForm(EMPTY_FORM)
          setFormError(null)
        }}
      >
        <form
          onSubmit={(e) => {
            e.preventDefault()
            if (!form.name.trim()) return
            saveMutation.mutate()
          }}
          className="space-y-4"
        >
          <div className="space-y-2">
            <label htmlFor="location-name" className="text-sm font-medium">Name</label>
            <input
              id="location-name"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              required
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="location-address" className="text-sm font-medium">Address</label>
            <textarea
              id="location-address"
              value={form.address}
              onChange={(e) => setForm({ ...form, address: e.target.value })}
              rows={3}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            />
          </div>
          <div className="space-y-2">
            <label id="location-manager-label" className="text-sm font-medium">Approval Manager</label>
            <UserCombobox
              users={allUsers}
              value={form.approvalManagerUserId}
              onChange={(userId) => setForm({ ...form, approvalManagerUserId: userId })}
            />
            <p className="text-xs text-muted-foreground">
              Any user can be an approval manager. They'll see an Approvals queue for requests routed to them.
            </p>
          </div>
          {formError && <p className="text-sm text-destructive">{formError}</p>}
          <button
            type="submit"
            disabled={saveMutation.isPending}
            className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
          >
            {saveMutation.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
            {editing ? 'Save Changes' : 'Create Location'}
          </button>
        </form>
      </FormDrawer>
    </div>
  )
}
