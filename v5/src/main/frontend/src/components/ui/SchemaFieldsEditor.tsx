import { useState } from 'react'
import { Plus, X } from 'lucide-react'
import type { SchemaField } from './SchemaForm'

const FIELD_TYPES: Array<NonNullable<SchemaField['type']>> = [
  'string',
  'textarea',
  'number',
  'boolean',
  'select',
  'select_with_other',
]

interface SchemaFieldsEditorProps {
  schema: SchemaField[]
  onChange: (schema: SchemaField[]) => void
}

function optionStrings(field: SchemaField): string[] {
  return (field.options ?? []).map((o) => (typeof o === 'string' ? o : o.value))
}

export function SchemaFieldsEditor({ schema, onChange }: SchemaFieldsEditorProps) {
  const [newOption, setNewOption] = useState<Record<number, string>>({})
  const [optionError, setOptionError] = useState<Record<number, string>>({})

  const updateField = (index: number, patch: Partial<SchemaField>) => {
    onChange(schema.map((f, i) => (i === index ? { ...f, ...patch } : f)))
  }

  const removeField = (index: number) => {
    onChange(schema.filter((_, i) => i !== index))
  }

  const addField = () => {
    onChange([...schema, { name: `field_${schema.length + 1}`, label: '', type: 'string', required: false }])
  }

  const addOption = (index: number) => {
    const raw = (newOption[index] ?? '').trim()
    if (!raw) return
    const existing = optionStrings(schema[index])
    if (existing.some((o) => o.toLowerCase() === raw.toLowerCase())) {
      setOptionError((e) => ({ ...e, [index]: `"${raw}" is already an option` }))
      return
    }
    setOptionError((e) => ({ ...e, [index]: '' }))
    updateField(index, { options: [...existing, raw] })
    setNewOption((n) => ({ ...n, [index]: '' }))
  }

  const removeOption = (index: number, option: string) => {
    updateField(index, { options: optionStrings(schema[index]).filter((o) => o !== option) })
  }

  return (
    <div className="space-y-3">
      {schema.map((field, i) => (
        <div key={i} className="rounded-lg border border-border bg-background p-3 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <input
              aria-label="Field name"
              value={field.name}
              onChange={(e) => updateField(i, { name: e.target.value })}
              placeholder="name"
              className="w-36 rounded-md border border-input bg-background px-2 py-1 text-xs outline-none focus:ring-2 focus:ring-ring"
            />
            <input
              aria-label="Field label"
              value={field.label ?? ''}
              onChange={(e) => updateField(i, { label: e.target.value })}
              placeholder="Label"
              className="w-40 rounded-md border border-input bg-background px-2 py-1 text-xs outline-none focus:ring-2 focus:ring-ring"
            />
            <select
              aria-label="Field type"
              value={field.type ?? 'string'}
              onChange={(e) => updateField(i, { type: e.target.value as SchemaField['type'] })}
              className="rounded-md border border-input bg-background px-2 py-1 text-xs outline-none focus:ring-2 focus:ring-ring"
            >
              {FIELD_TYPES.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
            <label className="inline-flex items-center gap-1 text-xs">
              <input
                type="checkbox"
                checked={field.required ?? false}
                onChange={(e) => updateField(i, { required: e.target.checked })}
                className="h-3.5 w-3.5 rounded border-border text-primary focus:ring-ring"
              />
              Required
            </label>
            <button
              type="button"
              onClick={() => removeField(i)}
              aria-label={`Remove field ${field.name}`}
              className="ml-auto inline-flex items-center rounded-md border border-border px-2 py-1 text-xs text-destructive hover:bg-muted"
            >
              <X className="h-3 w-3" />
            </button>
          </div>

          {(field.type === 'select' || field.type === 'select_with_other') && (
            <div className="space-y-2">
              <div className="flex flex-wrap items-center gap-1.5">
                {optionStrings(field).map((opt) => (
                  <span
                    key={opt}
                    className="inline-flex items-center gap-1 rounded-full bg-muted px-2.5 py-0.5 text-xs"
                  >
                    {opt}
                    <button
                      type="button"
                      onClick={() => removeOption(i, opt)}
                      aria-label={`Remove option ${opt}`}
                      className="text-muted-foreground hover:text-destructive"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </span>
                ))}
                {optionStrings(field).length === 0 && (
                  <span className="text-xs text-muted-foreground">No options yet.</span>
                )}
              </div>
              <div className="flex items-center gap-2">
                <input
                  aria-label={`Add option to ${field.name}`}
                  value={newOption[i] ?? ''}
                  onChange={(e) => {
                    setNewOption((n) => ({ ...n, [i]: e.target.value }))
                    setOptionError((er) => ({ ...er, [i]: '' }))
                  }}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault()
                      addOption(i)
                    }
                  }}
                  placeholder="Add option"
                  className="w-48 rounded-md border border-input bg-background px-2 py-1 text-xs outline-none focus:ring-2 focus:ring-ring"
                />
                <button
                  type="button"
                  onClick={() => addOption(i)}
                  className="inline-flex items-center gap-1 rounded-md border border-border px-2 py-1 text-xs hover:bg-muted"
                >
                  <Plus className="h-3 w-3" />
                  Add
                </button>
              </div>
              {optionError[i] && <p className="text-xs text-destructive">{optionError[i]}</p>}
              {field.type === 'select_with_other' && (
                <p className="text-xs text-muted-foreground">
                  An "Other" choice is appended automatically; the user can type a custom answer (max 255 chars).
                </p>
              )}
            </div>
          )}
        </div>
      ))}
      <button
        type="button"
        onClick={addField}
        className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-xs font-medium hover:bg-muted"
      >
        <Plus className="h-3 w-3" />
        Add Field
      </button>
    </div>
  )
}
