import type { ReactNode, FormEvent } from 'react'

export interface Field {
  name: string
  label: string
  type: 'text' | 'textarea' | 'select' | 'number' | 'boolean'
  options?: { value: string; label: string }[]
  required?: boolean
}

interface EntityFormProps {
  fields: Field[]
  values: Record<string, string>
  onChange: (name: string, value: string) => void
  onSubmit: (e: FormEvent) => void
  children?: ReactNode
  submitLabel?: string
  pending?: boolean
}

export function EntityForm({ fields, values, onChange, onSubmit, children, submitLabel = 'Submit', pending }: EntityFormProps) {
  return (
    <form onSubmit={onSubmit} className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        {fields.map((field) => (
          <div key={field.name} className="space-y-2">
            <label htmlFor={field.name} className="text-sm font-medium">
              {field.label}
              {field.required && <span className="text-destructive ml-1">*</span>}
            </label>
            {field.type === 'textarea' ? (
              <textarea
                id={field.name}
                name={field.name}
                value={values[field.name] ?? ''}
                onChange={(e) => onChange(field.name, e.target.value)}
                required={field.required}
                rows={3}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              />
            ) : field.type === 'select' ? (
              <select
                id={field.name}
                name={field.name}
                value={values[field.name] ?? ''}
                onChange={(e) => onChange(field.name, e.target.value)}
                required={field.required}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              >
                <option value="">Select…</option>
                {field.options?.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            ) : field.type === 'number' ? (
              <input
                id={field.name}
                name={field.name}
                type="number"
                value={values[field.name] ?? ''}
                onChange={(e) => onChange(field.name, e.target.value)}
                required={field.required}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              />
            ) : field.type === 'boolean' ? (
              <label className="inline-flex items-center gap-2">
                <input
                  id={field.name}
                  name={field.name}
                  type="checkbox"
                  checked={values[field.name] === 'true'}
                  onChange={(e) => onChange(field.name, e.target.checked ? 'true' : 'false')}
                  required={field.required}
                  className="h-4 w-4 rounded border-border text-primary focus:ring-ring"
                />
                <span className="text-sm text-muted-foreground">{field.label}</span>
              </label>
            ) : (
              <input
                id={field.name}
                name={field.name}
                type="text"
                value={values[field.name] ?? ''}
                onChange={(e) => onChange(field.name, e.target.value)}
                required={field.required}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              />
            )}
          </div>
        ))}
      </div>
      {children}
      <button
        type="submit"
        disabled={pending}
        className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        {submitLabel}
      </button>
    </form>
  )
}
