'use client'

import * as React from 'react'
import { api } from '@/lib/api'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'

type Field = {
  id: string
  name: string
  key: string
  type: string
  required: boolean
  options: string | null
  order: number
}

export function DynamicForm({
  issueType,
  value,
  onChange,
}: {
  issueType: string
  value: Record<string, any>
  onChange: (value: Record<string, any>) => void
}) {
  const [fields, setFields] = React.useState<Field[]>([])
  const [loading, setLoading] = React.useState(false)

  React.useEffect(() => {
    setLoading(true)
    api(`/forms/${issueType}`)
      .then((def: any) => {
        setFields(def.fields || [])
      })
      .catch(() => setFields([]))
      .finally(() => setLoading(false))
  }, [issueType])

  const update = (key: string, v: any) => {
    onChange({ ...value, [key]: v })
  }

  if (loading) return <p className="text-sm text-muted-foreground">Loading form fields...</p>
  if (fields.length === 0) return null

  return (
    <div className="space-y-4 rounded-lg border bg-background p-4">
      <p className="text-sm font-medium text-muted-foreground">Additional details</p>
      {fields.map((field) => {
        const val = value?.[field.key]
        const options = field.options ? (JSON.parse(field.options) as string[]) : []
        return (
          <div key={field.id}>
            <label className="text-sm font-medium">
              {field.name} {field.required && <span className="text-destructive">*</span>}
            </label>
            {field.type === 'TEXT' && (
              <Input value={val || ''} onChange={(e) => update(field.key, e.target.value)} required={field.required} />
            )}
            {field.type === 'NUMBER' && (
              <Input
                type="number"
                value={val || ''}
                onChange={(e) => update(field.key, Number(e.target.value))}
                required={field.required}
              />
            )}
            {field.type === 'DATE' && (
              <Input
                type="date"
                value={val || ''}
                onChange={(e) => update(field.key, e.target.value)}
                required={field.required}
              />
            )}
            {field.type === 'SELECT' && (
              <Select
                value={val || ''}
                onChange={(e) => update(field.key, e.target.value)}
                required={field.required}
              >
                <option value="">Select {field.name}</option>
                {options.map((o) => (
                  <option key={o} value={o}>
                    {o}
                  </option>
                ))}
              </Select>
            )}
            {field.type === 'BOOLEAN' && (
              <div className="flex items-center gap-2">
                <Switch
                  checked={!!val}
                  onChange={(e: any) => update(field.key, e.target.checked)}
                />
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}
