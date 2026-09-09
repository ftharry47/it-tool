import { useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Loader2, Plus, RefreshCw, Search } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { DataTable } from '../../components/ui/DataTable'
import { FormDrawer } from '../../components/ui/FormDrawer'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { ToastStack, type ToastItem } from '../../components/ui/Toast'

interface User {
  id: string
  email: string
  displayName: string
  jobTitle: string | null
  department: string | null
  active: boolean
  roles: string[]
}

const ROLES = ['END_USER', 'AGENT', 'TEAM_LEAD', 'ADMIN', 'SUPER_ADMIN']

export function UserAdmin() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()

  const [form, setForm] = useState({
    email: '',
    displayName: '',
    jobTitle: '',
    department: '',
    roleName: 'END_USER',
  })
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [syncResult, setSyncResult] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const [pendingRole, setPendingRole] = useState<{ userId: string; roleName: string; name: string } | null>(null)
  const [pendingActive, setPendingActive] = useState<{ userId: string; isActive: boolean; name: string } | null>(null)

  const pushToast = (type: ToastItem['type'], message: string) => {
    setToasts((prev) => [...prev, { id: crypto.randomUUID(), type, message }])
  }
  const dismissToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }

  const usersQuery = useQuery<User[]>({
    queryKey: ['admin-users'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/users')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const createMutation = useMutation<User, Error>({
    mutationFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/users', {
        method: 'POST',
        body: JSON.stringify(form),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      setForm({ email: '', displayName: '', jobTitle: '', department: '', roleName: 'END_USER' })
      setFormError(null)
      setDrawerOpen(false)
      pushToast('success', 'User created successfully')
    },
    onError: (error) => {
      console.error('User create failed:', error)
      setFormError('Could not create the user. Please try again or contact IT support.')
      pushToast('error', 'Could not create the user.')
    },
  })

  const roleMutation = useMutation<User, Error, { userId: string; roleName: string }>({
    mutationFn: async ({ userId, roleName }) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/users/${userId}/role`, {
        method: 'PATCH',
        body: JSON.stringify({ roleName }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      setFormError(null)
      pushToast('success', 'Role updated')
    },
    onError: (error) => {
      console.error('Role update failed:', error)
      pushToast('error', 'Could not update the role. Please try again.')
    },
  })

  const activeMutation = useMutation<User, Error, { userId: string; isActive: boolean }>({
    mutationFn: async ({ userId, isActive }) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/users/${userId}`, {
        method: 'PATCH',
        body: JSON.stringify({ isActive }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    onSuccess: (_data, { isActive }) => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      setFormError(null)
      pushToast('success', isActive ? 'User activated' : 'User deactivated')
    },
    onError: (error) => {
      console.error('User status update failed:', error)
      pushToast('error', 'Could not update the user status. Please try again.')
    },
  })

  const syncMutation = useMutation<{ added: number; updated: number; skipped: number; errors: number }, Error>({
    mutationFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/users/sync-from-ad', { method: 'POST' })
      if (!res.ok) {
        let message = `HTTP ${res.status}`
        try {
          const body = await res.json()
          if (body?.error) message = body.error
        } catch { /* keep generic message */ }
        throw new Error(message)
      }
      return res.json()
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      setSyncResult(`Sync complete: ${result.added} added, ${result.updated} updated, ${result.skipped} skipped.`)
      setFormError(null)
      pushToast('success', 'AD sync complete')
    },
    onError: (error) => {
      console.error('AD sync failed:', error)
      setSyncResult(null)
      setFormError('AD sync failed. Please try again or contact IT support.')
      pushToast('error', 'AD sync failed.')
    },
  })

  const filteredUsers = (usersQuery.data ?? []).filter((u) => {
    const q = search.trim().toLowerCase()
    if (!q) return true
    return (
      (u.displayName ?? '').toLowerCase().includes(q) ||
      (u.email ?? '').toLowerCase().includes(q) ||
      (u.department ?? '').toLowerCase().includes(q)
    )
  })

  if (usersQuery.isLoading) return <Loading />
  if (usersQuery.error) return <ErrorFallback error={usersQuery.error} message="Could not load users." onRetry={() => usersQuery.refetch()} />

  const createDrawerDirty = form.email !== '' || form.displayName !== '' || form.jobTitle !== '' || form.department !== '' || form.roleName !== 'END_USER'

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <ToastStack toasts={toasts} onDismiss={dismissToast} />
      <ConfirmDialog
        open={pendingRole !== null}
        title="Change role?"
        description={pendingRole ? `Change role for ${pendingRole.name} to ${pendingRole.roleName}?` : undefined}
        confirmLabel="Change Role"
        onConfirm={() => {
          if (pendingRole) {
            roleMutation.mutate({ userId: pendingRole.userId, roleName: pendingRole.roleName })
          }
          setPendingRole(null)
        }}
        onCancel={() => setPendingRole(null)}
      />
      <ConfirmDialog
        open={pendingActive !== null}
        title={pendingActive?.isActive ? 'Activate user?' : 'Deactivate user?'}
        description={pendingActive ? `Are you sure you want to ${pendingActive.isActive ? 'activate' : 'deactivate'} ${pendingActive.name}?` : undefined}
        confirmLabel={pendingActive?.isActive ? 'Activate' : 'Deactivate'}
        destructive={!pendingActive?.isActive}
        onConfirm={() => {
          if (pendingActive) {
            activeMutation.mutate({ userId: pendingActive.userId, isActive: pendingActive.isActive })
          }
          setPendingActive(null)
        }}
        onCancel={() => setPendingActive(null)}
      />
      <div className="mx-auto max-w-5xl space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold tracking-tight">User Admin</h1>
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setDrawerOpen(true)}
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <Plus className="h-4 w-4" />
              New User
            </button>
            <button
              type="button"
              onClick={() => { setSyncResult(null); syncMutation.mutate() }}
              disabled={syncMutation.isPending}
              className="inline-flex items-center gap-2 rounded-md border border-border bg-background px-4 py-2 text-sm font-medium transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              {syncMutation.isPending
                ? <Loader2 className="h-4 w-4 animate-spin" />
                : <RefreshCw className="h-4 w-4" />}
              {syncMutation.isPending ? 'Syncing…' : 'Sync from AD'}
            </button>
          </div>
        </div>
        {syncResult && <p className="text-sm text-green-700">{syncResult}</p>}

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <div className="mb-4 flex items-center justify-between gap-4">
            <h2 className="text-lg font-semibold">Users</h2>
            <div className="relative w-72">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <input
                type="search"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search name, email, or department…"
                aria-label="Search users"
                className="w-full rounded-md border border-input bg-background py-2 pl-9 pr-3 text-sm outline-none focus:ring-2 focus:ring-ring"
              />
            </div>
          </div>
          <DataTable<User>
            caption="Users"
            columns={[
              { key: 'displayName', header: 'Name', render: (row) => row.displayName || '—' },
              { key: 'email', header: 'Email', render: (row) => row.email ?? '—' },
              { key: 'department', header: 'Department', render: (row) => row.department || '—' },
              {
                key: 'role',
                header: 'Role',
                render: (row) => (
                  <select
                    value={row.roles[0] ?? 'END_USER'}
                    onChange={(e) => setPendingRole({ userId: row.id, roleName: e.target.value, name: row.displayName || row.email })}
                    disabled={roleMutation.isPending}
                    className="rounded-md border border-input bg-background px-2 py-1 text-xs outline-none focus:ring-2 focus:ring-ring"
                  >
                    {ROLES.map((r) => (
                      <option key={r} value={r}>{r}</option>
                    ))}
                  </select>
                ),
              },
              {
                key: 'active',
                header: 'Active',
                render: (row) => (
                  <button
                    type="button"
                    onClick={() => setPendingActive({ userId: row.id, isActive: !row.active, name: row.displayName || row.email })}
                    disabled={activeMutation.isPending}
                    className={`rounded-full px-3 py-1 text-xs font-medium transition disabled:opacity-50 ${
                      row.active
                        ? 'bg-green-100 text-green-800 hover:bg-green-200'
                        : 'bg-red-100 text-red-800 hover:bg-red-200'
                    }`}
                  >
                    {row.active ? 'Active' : 'Inactive'}
                  </button>
                ),
              },
            ]}
            data={filteredUsers}
            getRowKey={(row) => row.id}
            emptyText={search.trim() ? `No users match "${search.trim()}".` : 'No users found.'}
          />
        </section>
      </div>

      <FormDrawer open={drawerOpen} title="Create User" dirty={createDrawerDirty} onClose={() => { setDrawerOpen(false); setFormError(null) }}>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            if (!form.email) return
            createMutation.mutate()
          }}
          className="grid gap-4 sm:grid-cols-2"
        >
          {formError && <p className="col-span-full text-sm text-red-600">{formError}</p>}
          <div className="space-y-2">
            <label htmlFor="email" className="text-sm font-medium">Email</label>
            <input
              id="email"
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              required
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="displayName" className="text-sm font-medium">Display Name</label>
            <input
              id="displayName"
              value={form.displayName}
              onChange={(e) => setForm({ ...form, displayName: e.target.value })}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="jobTitle" className="text-sm font-medium">Job Title</label>
            <input
              id="jobTitle"
              value={form.jobTitle}
              onChange={(e) => setForm({ ...form, jobTitle: e.target.value })}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="department" className="text-sm font-medium">Department</label>
            <input
              id="department"
              value={form.department}
              onChange={(e) => setForm({ ...form, department: e.target.value })}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="role" className="text-sm font-medium">Role</label>
            <select
              id="role"
              value={form.roleName}
              onChange={(e) => setForm({ ...form, roleName: e.target.value })}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            >
              {ROLES.map((r) => (
                <option key={r} value={r}>{r}</option>
              ))}
            </select>
          </div>
          <div className="flex items-end">
            <button
              type="submit"
              disabled={createMutation.isPending}
              className="inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
            >
              {createMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Create
            </button>
          </div>
        </form>
      </FormDrawer>
    </div>
  )
}
