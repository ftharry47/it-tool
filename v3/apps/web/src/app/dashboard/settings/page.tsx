'use client'

import { useEffect, useState } from 'react'
import { useAuth, api } from '@/lib/api'
import { Button } from '@/components/button'
import { Shield, Users, UserCog, Trash2 } from 'lucide-react'
import { cn } from '@/lib/utils'

interface Permission {
  id: string
  key: string
}

interface Role {
  id: string
  key: string
  name: string
  permissions: { permission: Permission }[]
}

interface User {
  id: string
  name: string | null
  email: string
  roles: { role: { id: string; key: string; name: string } }[]
  permissions: { permission: Permission }[]
}

export default function SettingsPage() {
  const { loaded, user, authed } = useAuth()
  const [tab, setTab] = useState<'users' | 'roles'>('users')
  const [users, setUsers] = useState<User[]>([])
  const [roles, setRoles] = useState<Role[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  const isAdmin = user?.permissions?.includes('admin:all')

  const fetchData = async () => {
    const [uRes, rRes] = await Promise.all([
      fetch(api('/api/users'), authed()),
      fetch(api('/api/roles'), authed()),
    ])
    if (uRes.ok) setUsers(await uRes.json())
    if (rRes.ok) setRoles(await rRes.json())
    setLoading(false)
  }

  useEffect(() => {
    if (isAdmin) fetchData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAdmin])

  const assignRole = async (userId: string, roleId: string) => {
    if (!roleId) return
    setSaving(true)
    await fetch(api('/api/user-roles'), authed({
      method: 'POST',
      body: JSON.stringify({ userId, roleId }),
    }))
    await fetchData()
    setSaving(false)
  }

  const removeRole = async (userId: string, roleId: string) => {
    setSaving(true)
    await fetch(api(`/api/user-roles/${userId}/${roleId}`), authed({
      method: 'DELETE',
    }))
    await fetchData()
    setSaving(false)
  }

  if (!loaded) return <p className="text-sm text-muted-foreground">Loading...</p>
  if (!isAdmin) {
    return (
      <div className="space-y-4">
        <h1 className="text-2xl font-semibold">Settings</h1>
        <p className="text-muted-foreground">You do not have permission to view this page.</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Settings</h1>

      <div className="flex gap-2 border-b pb-2">
        <button
          onClick={() => setTab('users')}
          className={cn(
            'flex items-center gap-2 rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
            tab === 'users' ? 'bg-accent text-accent-foreground' : 'text-muted-foreground hover:text-foreground'
          )}
        >
          <Users className="h-4 w-4" /> Users
        </button>
        <button
          onClick={() => setTab('roles')}
          className={cn(
            'flex items-center gap-2 rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
            tab === 'roles' ? 'bg-accent text-accent-foreground' : 'text-muted-foreground hover:text-foreground'
          )}
        >
          <Shield className="h-4 w-4" /> Roles
        </button>
      </div>

      {loading ? (
        <p className="text-sm text-muted-foreground">Loading...</p>
      ) : tab === 'users' ? (
        <div className="rounded-lg border">
          <table className="w-full text-sm">
            <thead className="bg-muted/50 text-left text-xs uppercase text-muted-foreground">
              <tr>
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="px-4 py-3 font-medium">Email</th>
                <th className="px-4 py-3 font-medium">Current Roles</th>
                <th className="px-4 py-3 font-medium">Add Role</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {users.map((u) => (
                <tr key={u.id}>
                  <td className="px-4 py-3">{u.name || '—'}</td>
                  <td className="px-4 py-3">{u.email}</td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1">
                      {u.roles.map((ur) => (
                        <span key={ur.role.id} className="inline-flex items-center gap-1 rounded border px-2 py-0.5 text-xs">
                          {ur.role.name}
                          <button
                            onClick={() => removeRole(u.id, ur.role.id)}
                            disabled={saving}
                            className="text-muted-foreground hover:text-destructive"
                            title="Remove"
                          >
                            <Trash2 className="h-3 w-3" />
                          </button>
                        </span>
                      ))}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <select
                      disabled={saving}
                      onChange={(e) => { assignRole(u.id, e.target.value); e.target.value = '' }}
                      className="rounded-md border bg-background px-2 py-1 text-sm outline-none"
                    >
                      <option value="">Select role...</option>
                      {roles
                        .filter((r) => !u.roles.some((ur) => ur.role.id === r.id))
                        .map((r) => (
                          <option key={r.id} value={r.id}>{r.name}</option>
                        ))}
                    </select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {roles.map((r) => (
            <div key={r.id} className="rounded-lg border p-4">
              <div className="mb-2 flex items-center gap-2">
                <UserCog className="h-4 w-4 text-primary" />
                <h3 className="font-medium">{r.name}</h3>
                <span className="font-mono text-xs text-muted-foreground">{r.key}</span>
              </div>
              <p className="mb-2 text-xs text-muted-foreground">Permissions</p>
              <div className="flex flex-wrap gap-1">
                {r.permissions.map((p) => (
                  <span key={p.permission.id} className="rounded bg-muted px-2 py-0.5 text-xs">
                    {p.permission.key}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
