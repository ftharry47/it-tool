import { useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Users, X } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { ToastStack, type ToastItem } from '../../components/ui/Toast'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { UserCombobox } from '../../components/ui/UserCombobox'
import { useDocumentTitle } from '../../components/layout/useDocumentTitle'
import { useSmartBack } from '../../lib/useSmartBack'

interface TeamMemberInfo {
  userId: string
  displayName: string
  email: string
}

interface Team {
  id: string
  name: string
  description: string | null
  members: TeamMemberInfo[]
}

interface OrgUser {
  id: string
  displayName: string
  email: string
}

const TIER_TEAM_NAMES = ['L1 Support', 'L2 Support', 'L3 Support']

function TeamCard({
  team,
  allUsers,
  onAdd,
  onRemove,
  busy,
}: {
  team: Team
  allUsers: OrgUser[]
  onAdd: (teamId: string, userId: string) => void
  onRemove: (teamId: string, userId: string) => void
  busy: boolean
}) {
  const [pendingAddUserId, setPendingAddUserId] = useState('')
  const [pendingRemove, setPendingRemove] = useState<TeamMemberInfo | null>(null)
  const [addConfirmOpen, setAddConfirmOpen] = useState(false)
  const [removeConfirmOpen, setRemoveConfirmOpen] = useState(false)

  const memberIds = new Set(team.members.map((m) => m.userId))
  const candidates = allUsers.filter((u) => !memberIds.has(u.id))

  const pendingAddUser = candidates.find((u) => u.id === pendingAddUserId)

  function handleAddSelect(userId: string) {
    if (!userId) return
    setPendingAddUserId(userId)
    setAddConfirmOpen(true)
  }

  function confirmAdd() {
    onAdd(team.id, pendingAddUserId)
    setAddConfirmOpen(false)
    setPendingAddUserId('')
  }

  function cancelAdd() {
    setAddConfirmOpen(false)
    setPendingAddUserId('')
  }

  function handleRemoveClick(m: TeamMemberInfo) {
    setPendingRemove(m)
    setRemoveConfirmOpen(true)
  }

  function confirmRemove() {
    if (pendingRemove) {
      onRemove(team.id, pendingRemove.userId)
    }
    setRemoveConfirmOpen(false)
    setPendingRemove(null)
  }

  function cancelRemove() {
    setRemoveConfirmOpen(false)
    setPendingRemove(null)
  }

  return (
    <div className="flex flex-col rounded-xl border border-border bg-card shadow-sm">
      <div className="border-b border-border px-5 py-4">
        <h2 className="flex items-center gap-2 text-base font-semibold tracking-tight">
          <Users className="h-4 w-4 text-muted-foreground" />
          {team.name}
        </h2>
        {team.description && (
          <p className="mt-0.5 text-xs text-muted-foreground">{team.description}</p>
        )}
      </div>

      <div className="flex-1 px-5 py-4">
        {team.members.length === 0 ? (
          <p className="rounded-md border border-dashed border-border bg-muted/40 px-3 py-3 text-xs text-muted-foreground">
            No members yet — escalation notifications for this tier will go nowhere until someone is added.
          </p>
        ) : (
          <ul className="divide-y divide-border/60">
            {team.members.map((m) => (
              <li key={m.userId} className="flex items-center justify-between gap-2 py-2">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{m.displayName}</p>
                  <p className="truncate text-xs text-muted-foreground">{m.email}</p>
                </div>
                <button
                  type="button"
                  onClick={() => handleRemoveClick(m)}
                  disabled={busy}
                  aria-label={`Remove ${m.displayName} from ${team.name}`}
                  className="shrink-0 rounded-md p-1 text-muted-foreground transition hover:bg-muted hover:text-destructive focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50"
                >
                  <X className="h-4 w-4" />
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="border-t border-border px-5 py-3">
        <UserCombobox
          users={candidates}
          value={pendingAddUserId}
          onChange={handleAddSelect}
          placeholder="Add member…"
          searchPlaceholder="Search name or email…"
          clearable={false}
          disabled={busy}
        />
      </div>

      <ConfirmDialog
        open={addConfirmOpen}
        title={`Add ${pendingAddUser ? pendingAddUser.displayName || pendingAddUser.email : 'member'} to ${team.name}?`}
        description="They will be added to this team and receive escalation notifications for this tier."
        confirmLabel="Add"
        onConfirm={confirmAdd}
        onCancel={cancelAdd}
      />

      <ConfirmDialog
        open={removeConfirmOpen}
        title={`Remove ${pendingRemove ? pendingRemove.displayName || pendingRemove.email : 'member'} from ${team.name}?`}
        description="They will no longer receive escalation notifications for this tier."
        destructive
        confirmLabel="Remove"
        onConfirm={confirmRemove}
        onCancel={cancelRemove}
      />
    </div>
  )
}

export function SupportTiers() {
  const smartBack = useSmartBack('/admin')
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()
  useDocumentTitle('Support Tiers')

  const [toasts, setToasts] = useState<ToastItem[]>([])
  const pushToast = (type: ToastItem['type'], message: string) => {
    setToasts((prev) => [...prev, { id: crypto.randomUUID(), type, message }])
  }
  const dismissToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }

  const teamsQuery = useQuery<Team[]>({
    queryKey: ['teams'],
    enabled: !!account,
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/teams')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const usersQuery = useQuery<OrgUser[]>({
    queryKey: ['users'],
    enabled: !!account,
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/users')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const addMutation = useMutation<void, Error, { teamId: string; userId: string }>({
    mutationFn: async ({ teamId, userId }) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/teams/${teamId}/members`, {
        method: 'POST',
        body: JSON.stringify({ userId }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['teams'] })
      pushToast('success', 'Member added')
    },
    onError: (error) => {
      console.error('Add member failed:', error)
      pushToast('error', 'Could not add the member. Please try again or contact IT support.')
    },
  })

  const removeMutation = useMutation<void, Error, { teamId: string; userId: string }>({
    mutationFn: async ({ teamId, userId }) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/teams/${teamId}/members/${userId}`, {
        method: 'DELETE',
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['teams'] })
      pushToast('success', 'Member removed')
    },
    onError: (error) => {
      console.error('Remove member failed:', error)
      pushToast('error', 'Could not remove the member. Please try again or contact IT support.')
    },
  })

  if (teamsQuery.isLoading || usersQuery.isLoading) return <Loading />
  if (teamsQuery.error) {
    return <ErrorFallback error={teamsQuery.error} message="Could not load teams." onRetry={() => teamsQuery.refetch()} />
  }

  const teams = teamsQuery.data ?? []
  const tierTeams = TIER_TEAM_NAMES
    .map((name) => teams.find((t) => t.name === name))
    .filter((t): t is Team => !!t)
  const otherTeams = teams.filter((t) => !TIER_TEAM_NAMES.includes(t.name))
  const busy = addMutation.isPending || removeMutation.isPending
  const allUsers = usersQuery.data ?? []

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <ToastStack toasts={toasts} onDismiss={dismissToast} />
      <div className="mx-auto max-w-6xl space-y-6">
        <div>
          <div className="flex items-center gap-3">
            <button
                        onClick={smartBack}
                        className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      >
                        <ArrowLeft className="h-4 w-4" />
                        Back
                      </button>
            <div>
              <h1 className="text-2xl font-semibold tracking-tight">Support Tiers</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                          The L1/L2/L3 escalation roster. SLA escalation reassigns incidents to these teams.
                        </p>
            </div>
          </div>
        </div>

        <div className="grid gap-4 lg:grid-cols-3">
          {tierTeams.map((team) => (
            <TeamCard
              key={team.id}
              team={team}
              allUsers={allUsers}
              busy={busy}
              onAdd={(teamId, userId) => addMutation.mutate({ teamId, userId })}
              onRemove={(teamId, userId) => removeMutation.mutate({ teamId, userId })}
            />
          ))}
        </div>

        {otherTeams.length > 0 && (
          <div className="space-y-4">
            <h2 className="border-b border-border pb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              Other teams
            </h2>
            <div className="grid gap-4 lg:grid-cols-3">
              {otherTeams.map((team) => (
                <TeamCard
                  key={team.id}
                  team={team}
                  allUsers={allUsers}
                  busy={busy}
                  onAdd={(teamId, userId) => addMutation.mutate({ teamId, userId })}
                  onRemove={(teamId, userId) => removeMutation.mutate({ teamId, userId })}
                />
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
