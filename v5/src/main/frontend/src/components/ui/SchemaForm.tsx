import { useMemo, useState } from 'react'
import { EntityForm, type Field } from './EntityForm'

export const OTHER_OPTION_MAX_LENGTH = 255

export interface SchemaField {
  name: string
  label?: string
  type?: 'string' | 'textarea' | 'number' | 'boolean' | 'select' | 'select_with_other'
  required?: boolean
  options?: Array<{ value: string; label: string } | string>
}

interface SchemaFormProps {
  schema: SchemaField[]
  values: Record<string, string>
  onChange: (name: string, value: string) => void
  onSubmit: (values: Record<string, string>) => void
  submitLabel?: string
  pending?: boolean
  serverError?: string | null
}

function isValidSchemaField(f: unknown): f is SchemaField {
  if (typeof f !== 'object' || f === null) return false
  const field = f as Record<string, unknown>
  if (typeof field.name !== 'string' || !field.name) return false
  const allowedTypes = ['string', 'textarea', 'number', 'boolean', 'select', 'select_with_other']
  if (field.type !== undefined && !allowedTypes.includes(field.type as string)) return false
  return true
}

export function isValidSchema(v: unknown): v is SchemaField[] {
  if (!Array.isArray(v)) return false
  return v.every((f) => isValidSchemaField(f))
}

export function validateSchema(values: Record<string, string>, schema: SchemaField[]): string | null {
  for (const field of schema) {
    const value = values[field.name]
    if (field.required && (value === undefined || value === '')) {
      return `${field.label || field.name} is required`
    }
    if (field.type === 'number' && value !== undefined && value !== '') {
      if (Number.isNaN(Number(value))) {
        return `${field.label || field.name} must be a number`
      }
    }
    if (field.type === 'select' && field.options && value) {
      const allowed = field.options.map((opt) => (typeof opt === 'string' ? opt : opt.value))
      if (!allowed.includes(value)) {
        return `${field.label || field.name} has an invalid option`
      }
    }
    if (field.type === 'select_with_other' && value) {
      const allowed = (field.options ?? []).map((opt) => (typeof opt === 'string' ? opt : opt.value))
      if (!allowed.includes(value) && value.length > OTHER_OPTION_MAX_LENGTH) {
        return `${field.label || field.name} must be ${OTHER_OPTION_MAX_LENGTH} characters or fewer`
      }
    }
  }
  return null
}

export function toEntityFields(schema: SchemaField[]): Field[] {
  return schema.map((field) => {
    const type = (field.type ?? 'string') as 'string' | 'textarea' | 'number' | 'boolean' | 'select' | 'select_with_other'
    const options = field.options?.map((opt) =>
      typeof opt === 'string' ? { value: opt, label: opt } : opt
    )
    const entityType: Field['type'] =
      type === 'select' ? 'select'
        : type === 'select_with_other' ? 'select_with_other'
        : type === 'number' ? 'number'
        : type === 'boolean' ? 'boolean'
        : type === 'textarea' ? 'textarea'
        : 'text'
    return {
      name: field.name,
      label: field.label || field.name,
      type: entityType,
      required: field.required,
      options,
    }
  })
}

export function SchemaForm({ schema, values, onChange, onSubmit, submitLabel, pending, serverError }: SchemaFormProps) {
  const [clientError, setClientError] = useState<string | null>(null)
  const fields = useMemo(() => toEntityFields(schema), [schema])

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setClientError(null)
    const err = validateSchema(values, schema)
    if (err) {
      setClientError(err)
      return
    }
    onSubmit(values)
  }

  return (
    <>
      <EntityForm
        fields={fields}
        values={values}
        onChange={onChange}
        onSubmit={handleSubmit}
        submitLabel={submitLabel}
        pending={pending}
      />
      {clientError && <p className="mt-4 text-sm text-destructive">{clientError}</p>}
      {serverError && <p className="mt-4 text-sm text-destructive">{serverError}</p>}
    </>
  )
}
