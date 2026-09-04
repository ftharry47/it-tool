import { useMsal } from '@azure/msal-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { fetchWithToken } from '../../api/client'
import { DataTable } from '../../components/ui/DataTable'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { Loading } from '../../components/ui/Loading'

interface AutomationRuleResponse {
  id: string
  name: string
  description: string
  triggerType: 'CREATED' | 'UPDATED' | 'STATUS_CHANGED' | 'SPRINT_STARTED' | 'SPRINT_COMPLETED'
  triggerEntity: 'INCIDENT' | 'PROBLEM' | 'CHANGE' | 'REQUEST' | 'ISSUE'
  triggerConfig: string
  conditions: string
  actions: string
  active: boolean
}

interface AutomationRunLogResponse {
  id: string
  ruleId: string
  entityType: string
  entityId: string
  triggeredEvent: string
  status: string
  output: string
  error: string
  executedAt: string
}

const TRIGGER_TYPES = ['CREATED', 'UPDATED', 'STATUS_CHANGED', 'SPRINT_STARTED', 'SPRINT_COMPLETED'] as const
const TRIGGER_ENTITIES = ['INCIDENT', 'PROBLEM', 'CHANGE', 'REQUEST', 'ISSUE'] as const
const OPS = ['eq', 'ne', 'in', 'gt', 'gte', 'lt', 'lte'] as const
const ACTION_TYPES = [
  'SET_FIELD',
  'ADD_COMMENT',
  'SET_STATUS',
  'ASSIGN_TO_USER',
  'ASSIGN_TO_TEAM',
  'CALL_WEBHOOK',
  'SEND_NOTIFICATION',
] as const

interface Condition {
  field: string
  op: (typeof OPS)[number]
  value: string
  values: string[]
}

// Config fields for each action type are derived directly from the handler classes in
// src/main/java/com/alignedcardio/itsm/service/automation (and service/notification for SEND_NOTIFICATION).
// If a handler's expected JSON keys change, these fields must be updated too.
const ACTION_CONFIG_FIELDS: Record<string, { key: string; label: string; optional?: boolean }[]> = {
  // FieldUpdateHandler.java: expects entity (optional), field, value.
  SET_FIELD: [
    { key: 'entity', label: 'Entity (optional)', optional: true },
    { key: 'field', label: 'Field' },
    { key: 'value', label: 'Value' },
  ],
  // AddCommentHandler.java: expects entity (optional), body.
  ADD_COMMENT: [
    { key: 'entity', label: 'Entity (optional)', optional: true },
    { key: 'body', label: 'Comment body' },
  ],
  // StatusChangeHandler.java: expects entity (optional), value.
  SET_STATUS: [
    { key: 'entity', label: 'Entity (optional)', optional: true },
    { key: 'value', label: 'New status' },
  ],
  // AssignHandler.java: expects entity (optional), targetId for both ASSIGN_TO_USER and ASSIGN_TO_TEAM.
  ASSIGN_TO_USER: [
    { key: 'entity', label: 'Entity (optional)', optional: true },
    { key: 'targetId', label: 'User ID' },
  ],
  ASSIGN_TO_TEAM: [
    { key: 'entity', label: 'Entity (optional)', optional: true },
    { key: 'targetId', label: 'Team ID' },
  ],
  // WebhookHandler.java: expects url, method (optional, default GET), body (optional).
  CALL_WEBHOOK: [
    { key: 'url', label: 'URL' },
    { key: 'method', label: 'Method (GET/POST/PUT/PATCH)', optional: true },
    { key: 'body', label: 'Body JSON', optional: true },
  ],
  // NotificationHandler.java (service/notification): expects userId, subject, body, channel (optional, default BOTH).
  SEND_NOTIFICATION: [
    { key: 'userId', label: 'User ID' },
    { key: 'subject', label: 'Subject' },
    { key: 'body', label: 'Body' },
    { key: 'channel', label: 'Channel (IN_APP/EMAIL/BOTH)', optional: true },
  ],
}

interface ActionRow {
  type: (typeof ACTION_TYPES)[number]
  config: Record<string, string>
}

function safeJsonParse(text: string): unknown {
  if (!text || text.trim() === '') return null
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function prettyJson(value: unknown) {
  return JSON.stringify(value, null, 2)
}

export function AutomationAdmin() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()

  const [editing, setEditing] = useState<AutomationRuleResponse | null>(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [triggerType, setTriggerType] = useState<AutomationRuleResponse['triggerType']>('CREATED')
  const [triggerEntity, setTriggerEntity] = useState<AutomationRuleResponse['triggerEntity']>('INCIDENT')
  const [triggerConfig, setTriggerConfig] = useState('{}')
  const [conditions, setConditions] = useState<Condition[]>([])
  const [actions, setActions] = useState<ActionRow[]>([])
  const [active, setActive] = useState(true)

  const [testPayload, setTestPayload] = useState('{}')
  const [testEntityId, setTestEntityId] = useState('')
  const [testResult, setTestResult] = useState<{ matched: boolean; actions: unknown[] } | null>(null)
  const [runLogId, setRunLogId] = useState<string | null>(null)

  const rulesQuery = useQuery<AutomationRuleResponse[]>({
    queryKey: ['automation-rules'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/automation/rules')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const runLogQuery = useQuery<AutomationRunLogResponse[]>({
    queryKey: ['automation-rule-runs', runLogId],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/automation/rules/${runLogId}/runs`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account && !!runLogId,
  })

  const loadRule = (rule: AutomationRuleResponse) => {
    setEditing(rule)
    setName(rule.name)
    setDescription(rule.description ?? '')
    setTriggerType(rule.triggerType)
    setTriggerEntity(rule.triggerEntity)
    setTriggerConfig(prettyJson(safeJsonParse(rule.triggerConfig)) ?? '{}')
    const parsedConditions = (safeJsonParse(rule.conditions) as Condition[] | Condition | null) ?? []
    setConditions(Array.isArray(parsedConditions) ? parsedConditions : [parsedConditions])
    const parsedActions = (safeJsonParse(rule.actions) as Record<string, string>[] | null) ?? []
    setActions(
      Array.isArray(parsedActions)
        ? parsedActions.map((a) => {
            const { type, ...config } = a
            return { type: (type as any) ?? 'SET_FIELD', config }
          })
        : []
    )
    setActive(rule.active)
  }

  const resetForm = () => {
    setEditing(null)
    setName('')
    setDescription('')
    setTriggerType('CREATED')
    setTriggerEntity('INCIDENT')
    setTriggerConfig('{}')
    setConditions([])
    setActions([])
    setActive(true)
    setTestPayload('{}')
    setTestEntityId('')
    setTestResult(null)
  }

  const buildRuleBody = (): Record<string, unknown> => {
    const conds = conditions
      .filter((c) => c.field.trim())
      .map((c) => {
        if (c.op === 'in') {
          return { field: c.field, op: c.op, values: c.values.filter((v) => v.trim() !== '') }
        }
        return { field: c.field, op: c.op, value: c.value }
      })
    const acts = actions.map((a) => ({ type: a.type, ...a.config }))
    return {
      name,
      description,
      triggerType,
      triggerEntity,
      triggerConfig: triggerConfig.trim() || '{}',
      conditions: JSON.stringify(conds),
      actions: JSON.stringify(acts),
      active,
    }
  }

  const createMutation = useMutation<AutomationRuleResponse, Error>({
    mutationFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/automation/rules', { method: 'POST', body: JSON.stringify(buildRuleBody()) })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      resetForm()
      queryClient.refetchQueries({ queryKey: ['automation-rules'], type: 'active' })
    },
    onError: (error) => {
      // eslint-disable-next-line no-console
      console.error('Create rule failed:', error)
    },
  })

  const updateMutation = useMutation<AutomationRuleResponse, Error>({
    mutationFn: async () => {
      if (!editing) throw new Error('No rule selected')
      const res = await fetchWithToken(instance, account!, `/api/v1/automation/rules/${editing.id}`, { method: 'PATCH', body: JSON.stringify(buildRuleBody()) })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      resetForm()
      queryClient.refetchQueries({ queryKey: ['automation-rules'], type: 'active' })
    },
  })

  const deleteMutation = useMutation<void, Error, string>({
    mutationFn: async (id) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/automation/rules/${id}`, { method: 'DELETE' })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
    },
    onSuccess: () => queryClient.refetchQueries({ queryKey: ['automation-rules'], type: 'active' }),
  })

  const testMutation = useMutation<{ matched: boolean; actions: unknown[] }, Error>({
    mutationFn: async () => {
      if (!editing) throw new Error('Save a rule before testing')
      const body = { samplePayload: safeJsonParse(testPayload) ?? {}, entityId: testEntityId || undefined }
      const res = await fetchWithToken(instance, account!, `/api/v1/automation/rules/${editing.id}/test`, { method: 'POST', body: JSON.stringify(body) })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: (data) => setTestResult(data),
  })

  const addCondition = () => setConditions([...conditions, { field: '', op: 'eq', value: '', values: [''] }])
  const removeCondition = (idx: number) => setConditions(conditions.filter((_, i) => i !== idx))
  const updateCondition = (idx: number, patch: Partial<Condition>) => {
    const next = [...conditions]
    next[idx] = { ...next[idx], ...patch }
    setConditions(next)
  }
  const addValue = (idx: number) => {
    const next = [...conditions]
    next[idx].values.push('')
    setConditions(next)
  }
  const updateValue = (idx: number, vIdx: number, value: string) => {
    const next = [...conditions]
    next[idx].values[vIdx] = value
    setConditions(next)
  }

  const addAction = () => setActions([...actions, { type: 'SET_FIELD', config: {} }])
  const removeAction = (idx: number) => setActions(actions.filter((_, i) => i !== idx))
  const updateAction = (idx: number, patch: Partial<ActionRow>) => {
    const next = [...actions]
    next[idx] = { ...next[idx], ...patch }
    setActions(next)
  }
  const updateActionConfig = (idx: number, key: string, value: string) => {
    const next = [...actions]
    next[idx] = { ...next[idx], config: { ...next[idx].config, [key]: value } }
    setActions(next)
  }

  if (rulesQuery.isLoading) return <Loading />
  if (rulesQuery.error) return <ErrorFallback error={rulesQuery.error} message="Could not load automation rules." onRetry={() => rulesQuery.refetch()} />

  const rules = rulesQuery.data ?? []

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold tracking-tight">Automation Rules</h1>
          <button onClick={resetForm} className="rounded-md border border-border px-3 py-1.5 text-sm transition hover:bg-muted">New Rule</button>
        </div>

        <DataTable<AutomationRuleResponse>
          caption="Rules"
          columns={[
            { key: 'name', header: 'Name' },
            { key: 'triggerEntity', header: 'Entity' },
            { key: 'triggerType', header: 'Trigger' },
            {
              key: 'active',
              header: 'Active',
              render: (row) => <span className={row.active ? 'text-green-600' : 'text-muted-foreground'}>{row.active ? 'Yes' : 'No'}</span>,
            },
            {
              key: 'actions',
              header: 'Actions',
              render: (row) => (
                <div className="flex gap-2">
                  <button onClick={() => loadRule(row)} className="text-sm text-primary hover:underline">Edit</button>
                  <button onClick={() => setRunLogId(row.id)} className="text-sm text-primary hover:underline">Runs</button>
                  <button onClick={() => deleteMutation.mutate(row.id)} className="text-sm text-destructive hover:underline">Delete</button>
                </div>
              ),
            },
          ]}
          data={rules}
          getRowKey={(row) => row.id}
          emptyText="No automation rules."
        />

        <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold">{editing ? `Edit ${editing.name}` : 'Create Rule'}</h2>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Rule name" className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
            <input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Description" className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
            <select value={triggerType} onChange={(e) => setTriggerType(e.target.value as any)} className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring">
              {TRIGGER_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
            <select value={triggerEntity} onChange={(e) => setTriggerEntity(e.target.value as any)} className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring">
              {TRIGGER_ENTITIES.map((e) => <option key={e} value={e}>{e}</option>)}
            </select>
          </div>

          <div className="mt-4">
            <p className="mb-1 text-sm font-medium">Trigger config (JSON)</p>
            <textarea value={triggerConfig} onChange={(e) => setTriggerConfig(e.target.value)} rows={2} className="w-full rounded-md border border-input bg-background p-2 font-mono text-xs outline-none focus:ring-2 focus:ring-ring" />
          </div>

          <div className="mt-6">
            <div className="mb-2 flex items-center justify-between">
              <h3 className="font-semibold">Conditions</h3>
              <button onClick={addCondition} className="rounded-md border border-border px-2 py-1 text-xs transition hover:bg-muted">+ Add condition</button>
            </div>
            {conditions.length === 0 && <p className="text-sm text-muted-foreground">No conditions — rule matches every payload.</p>}
            {conditions.map((c, i) => (
              <div key={i} className="mb-2 grid grid-cols-1 gap-2 rounded-md border border-border p-3 md:grid-cols-12">
                <input value={c.field} onChange={(e) => updateCondition(i, { field: e.target.value })} placeholder="Field (dot path)" className="md:col-span-3 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
                <select value={c.op} onChange={(e) => updateCondition(i, { op: e.target.value as any })} className="md:col-span-2 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring">
                  {OPS.map((o) => <option key={o} value={o}>{o}</option>)}
                </select>
                {c.op === 'in' ? (
                  <div className="md:col-span-5 space-y-1">
                    {c.values.map((v, vi) => (
                      <input key={vi} value={v} onChange={(e) => updateValue(i, vi, e.target.value)} placeholder="Value" className="w-full rounded-md border border-input bg-background px-3 py-1.5 text-sm outline-none focus:ring-2 focus:ring-ring" />
                    ))}
                    <button onClick={() => addValue(i)} className="text-xs text-primary hover:underline">+ Add value</button>
                  </div>
                ) : (
                  <input value={c.value} onChange={(e) => updateCondition(i, { value: e.target.value })} placeholder="Value" className="md:col-span-5 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
                )}
                <button onClick={() => removeCondition(i)} className="md:col-span-2 text-sm text-destructive hover:underline">Remove</button>
              </div>
            ))}
          </div>

          <div className="mt-6">
            <div className="mb-2 flex items-center justify-between">
              <h3 className="font-semibold">Actions</h3>
              <button onClick={addAction} className="rounded-md border border-border px-2 py-1 text-xs transition hover:bg-muted">+ Add action</button>
            </div>
            {actions.length === 0 && <p className="text-sm text-muted-foreground">No actions.</p>}
            {actions.map((a, i) => {
              const fields = ACTION_CONFIG_FIELDS[a.type] ?? []
              return (
                <div key={i} className="mb-2 rounded-md border border-border p-3">
                  <div className="mb-2 flex items-center gap-2">
                    <select value={a.type} onChange={(e) => updateAction(i, { type: e.target.value as any, config: {} })} className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring">
                      {ACTION_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                    </select>
                    <button onClick={() => removeAction(i)} className="text-sm text-destructive hover:underline">Remove</button>
                  </div>
                  <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
                    {fields.map((f) => (
                      <input
                        key={f.key}
                        value={a.config[f.key] ?? ''}
                        onChange={(e) => updateActionConfig(i, f.key, e.target.value)}
                        placeholder={`${f.label}${f.optional ? '' : ' *'}`}
                        className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
                      />
                    ))}
                  </div>
                </div>
              )
            })}
          </div>

          <div className="mt-4 flex items-center gap-2">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} className="h-4 w-4" />
            <span className="text-sm font-medium">Active</span>
          </div>

          <div className="mt-4 flex items-center gap-3">
            {editing ? (
              <button onClick={() => updateMutation.mutate()} disabled={!name || updateMutation.isPending} className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50">Update Rule</button>
            ) : (
              <button onClick={() => createMutation.mutate()} disabled={!name || createMutation.isPending} className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50">Create Rule</button>
            )}
            {createMutation.error && <p className="text-sm text-destructive">{createMutation.error.message}</p>}
            {updateMutation.error && <p className="text-sm text-destructive">{updateMutation.error.message}</p>}
          </div>
        </div>

        {editing && (
          <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
            <h2 className="mb-4 text-lg font-semibold">Dry-run: {editing.name}</h2>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <textarea value={testPayload} onChange={(e) => setTestPayload(e.target.value)} rows={6} placeholder="Sample payload JSON" className="w-full rounded-md border border-input bg-background p-2 font-mono text-xs outline-none focus:ring-2 focus:ring-ring" />
              <div className="space-y-3">
                <input value={testEntityId} onChange={(e) => setTestEntityId(e.target.value)} placeholder="Entity ID (optional)" className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring" />
                <button onClick={() => testMutation.mutate()} disabled={testMutation.isPending} className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50">Run Test</button>
                {testMutation.error && <p className="text-sm text-destructive">{testMutation.error.message}</p>}
                {testResult && (
                  <div className="rounded-md border border-border bg-muted p-3 text-sm">
                    <p className="font-medium">Matched: {testResult.matched ? 'Yes' : 'No'}</p>
                    {testResult.matched && (
                      <pre className="mt-1 overflow-auto text-xs">{prettyJson(testResult.actions)}</pre>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {runLogId && (
          <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-lg font-semibold">Run Log</h2>
              <button onClick={() => setRunLogId(null)} className="rounded-md border border-border px-2 py-1 text-sm transition hover:bg-muted">Close</button>
            </div>
            {runLogQuery.isLoading && <Loading />}
            {runLogQuery.error && <ErrorFallback error={runLogQuery.error} message="Could not load run log." onRetry={() => runLogQuery.refetch()} />}
            {runLogQuery.data && runLogQuery.data.length === 0 && <p className="text-sm text-muted-foreground">No runs recorded.</p>}
            {runLogQuery.data && runLogQuery.data.length > 0 && (
              <div className="space-y-2">
                {runLogQuery.data.map((run) => (
                  <div key={run.id} className="rounded-md border border-border p-3 text-sm">
                    <div className="flex items-center gap-2">
                      <span className={`font-semibold ${run.status === 'EXECUTED' ? 'text-green-600' : run.status === 'NO_MATCH' ? 'text-muted-foreground' : 'text-destructive'}`}>{run.status}</span>
                      <span className="text-muted-foreground">· {run.triggeredEvent} · {new Date(run.executedAt).toLocaleString()}</span>
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground">Entity: {run.entityType} {run.entityId}</p>
                    {run.output && <p className="mt-1 text-xs">Output: {run.output}</p>}
                    {run.error && <p className="mt-1 text-xs text-destructive">Error: {run.error}</p>}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
