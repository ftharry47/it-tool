import { useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Loader2, Plus } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'

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
  const [formError, setFormError] = useState<string | null>(null)

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
    },
    onError: (error) => setFormError(`Failed to create user: ${error.message}`),
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
    },
    onError: (error) => setFormError(`Failed to update role: ${error.message}`),
  })

  if (usersQuery.isLoading) return <Loading />
  if (usersQuery.error) return <ErrorFallback error={usersQuery.error} message="Could not load users." onRetry={() => usersQuery.refetch()} />

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-5xl space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">User Admin</h1>

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold">
            <Plus className="h-5 w-5" />
            Create User
          </h2>
          {formError && <p className="mb-4 text-sm text-red-600">{formError}</p>}
          <form
            onSubmit={(e) => {
              e.preventDefault()
              if (!form.email) return
              createMutation.mutate()
            }}
            className="grid gap-4 sm:grid-cols-2"
          >
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
        </section>

        <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold">Users</h2>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <th className="pb-2 pr-4 font-medium">Name</th>
                  <th className="pb-2 pr-4 font-medium">Email</th>
                  <th className="pb-2 pr-4 font-medium">Department</th>
                  <th className="pb-2 pr-4 font-medium">Role</th>
                  <th className="pb-2 pr-4 font-medium">Active</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {usersQuery.data?.map((u) => (
                  <tr key={u.id}>
                    <td className="py-3 pr-4">{u.displayName || '—'}</td>
                    <td className="py-3 pr-4">{u.email}</td>
                    <td className="py-3 pr-4">{u.department || '—'}</td>
                    <td className="py-3 pr-4">
                      <select
                        value={u.roles[0] ?? 'END_USER'}
                        onChange={(e) => roleMutation.mutate({ userId: u.id, roleName: e.target.value })}
                        disabled={roleMutation.isPending}
                        className="rounded-md border border-input bg-background px-2 py-1 text-xs outline-none focus:ring-2 focus:ring-ring"
                      >
                        {ROLES.map((r) => (
                          <option key={r} value={r}>{r}</option>
                        ))}
                      </select>
                    </td>
                    <td className="py-3 pr-4">{u.active ? 'Yes' : 'No'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </div>
  )
}
