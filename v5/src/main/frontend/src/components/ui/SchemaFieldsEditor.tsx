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

interface OptionObject {
  value: string
  label: string
  requiresApproval: boolean
}

function optionObjects(field: SchemaField): OptionObject[] {
  return (field.options ?? []).map((o) =>
    typeof o === 'string'
      ? { value: o, label: o, requiresApproval: false }
      : {
          value: o.value ?? '',
          label: o.label ?? o.value ?? '',
          requiresApproval: o.requiresApproval ?? false,
        }
  )
}

function valuesOnly(field: SchemaField): string[] {
  return optionObjects(field).map((o) => o.value)
}

interface SchemaFieldsEditorProps {
  schema: SchemaField[]
  onChange: (schema: SchemaField[]) => void
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
    const existing = valuesOnly(schema[index])
    if (existing.some((o) => o.toLowerCase() === raw.toLowerCase())) {
      setOptionError((e) => ({ ...e, [index]: `"${raw}" is already an option` }))
      return
    }
    setOptionError((e) => ({ ...e, [index]: '' }))
    const options = schema[index].options ?? []
    updateField(index, { options: [...options, { value: raw, label: raw, requiresApproval: false }] })
    setNewOption((n) => ({ ...n, [index]: '' }))
  }

  const removeOption = (index: number, optionValue: string) => {
    const options = schema[index].options?.filter((o) => (typeof o === 'string' ? o : o.value) !== optionValue)
    updateField(index, { options })
  }

  const toggleRequiresApproval = (index: number, optionValue: string) => {
    const options = (schema[index].options ?? []).map((o) => {
      if (typeof o === 'string') {
        if (o !== optionValue) return o
        return { value: o, label: o, requiresApproval: true }
      }
      if (o.value !== optionValue) return o
      return { ...o, requiresApproval: !o.requiresApproval }
    })
    updateField(index, { options })
  }

  const toggleOtherRequiresApproval = (index: number) => {
    updateField(index, { otherRequiresApproval: !(schema[index].otherRequiresApproval ?? false) })
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
                {optionObjects(field).map((opt) => (
                  <span
                    key={opt.value}
                    className="inline-flex items-center gap-2 rounded-full bg-muted px-2.5 py-0.5 text-xs"
                  >
                    <span>{opt.label}</span>
                    <label className="inline-flex cursor-pointer items-center gap-1 text-[10px] text-muted-foreground" title="Requires approval">
                      <input
                        type="checkbox"
                        checked={opt.requiresApproval}
                        onChange={() => toggleRequiresApproval(i, opt.value)}
                        className="h-3 w-3 rounded border-border text-primary focus:ring-ring"
                      />
                      approval
                    </label>
                    <button
                      type="button"
                      onClick={() => removeOption(i, opt.value)}
                      aria-label={`Remove option ${opt.label}`}
                      className="text-muted-foreground hover:text-destructive"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </span>
                ))}
                {optionObjects(field).length === 0 && (
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
                <label className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={field.otherRequiresApproval ?? false}
                    onChange={() => toggleOtherRequiresApproval(i)}
                    className="h-3.5 w-3.5 rounded border-border text-primary focus:ring-ring"
                  />
                  Other free-text answers require approval
                </label>
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
