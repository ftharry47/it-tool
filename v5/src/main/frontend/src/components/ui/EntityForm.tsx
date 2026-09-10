import { useState, type ReactNode, type FormEvent } from 'react'
import { Loader2 } from 'lucide-react'
import { DateTimeInput } from './DateTimeInput'

export const OTHER_SENTINEL = '__other__'

export interface Field {
  name: string
  label: string
  type: 'text' | 'textarea' | 'select' | 'select_with_other' | 'number' | 'boolean' | 'datetime-local'
  options?: { value: string; label: string }[]
  required?: boolean
  placeholder?: string
  disabled?: boolean
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
  const [otherSelected, setOtherSelected] = useState<Record<string, boolean>>({})

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
                placeholder={field.placeholder}
                rows={3}
                disabled={field.disabled}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring disabled:opacity-60 disabled:cursor-not-allowed"
              />
            ) : field.type === 'select' ? (
              <select
                id={field.name}
                name={field.name}
                value={values[field.name] ?? ''}
                onChange={(e) => onChange(field.name, e.target.value)}
                required={field.required}
                disabled={field.disabled}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring disabled:opacity-60 disabled:cursor-not-allowed"
              >
                <option value="">Select…</option>
                {field.options?.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            ) : field.type === 'select_with_other' ? (
              (() => {
                const current = values[field.name] ?? ''
                const isPreset = (field.options ?? []).some((o) => o.value === current)
                const showOtherInput = otherSelected[field.name] || (current !== '' && !isPreset)
                return (
                  <div className="space-y-2">
                    <select
                      id={field.name}
                      name={field.name}
                      value={showOtherInput ? OTHER_SENTINEL : current}
                      onChange={(e) => {
                        if (e.target.value === OTHER_SENTINEL) {
                          setOtherSelected((s) => ({ ...s, [field.name]: true }))
                          onChange(field.name, '')
                        } else {
                          setOtherSelected((s) => ({ ...s, [field.name]: false }))
                          onChange(field.name, e.target.value)
                        }
                      }}
                      required={field.required && !showOtherInput}
                      disabled={field.disabled}
                      className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring disabled:opacity-60 disabled:cursor-not-allowed"
                    >
                      <option value="">Select…</option>
                      {field.options?.map((opt) => (
                        <option key={opt.value} value={opt.value}>
                          {opt.label}
                        </option>
                      ))}
                      <option value={OTHER_SENTINEL}>Other</option>
                    </select>
                    {showOtherInput && (
                      <input
                        id={`${field.name}-other`}
                        name={`${field.name}-other`}
                        type="text"
                        value={isPreset ? '' : current}
                        onChange={(e) => onChange(field.name, e.target.value)}
                        required={field.required}
                        maxLength={255}
                        placeholder="Please specify"
                        disabled={field.disabled}
                        className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring disabled:opacity-60 disabled:cursor-not-allowed"
                      />
                    )}
                  </div>
                )
              })()
            ) : field.type === 'number' ? (
              <input
                id={field.name}
                name={field.name}
                type="number"
                value={values[field.name] ?? ''}
                onChange={(e) => onChange(field.name, e.target.value)}
                required={field.required}
                placeholder={field.placeholder}
                disabled={field.disabled}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring disabled:opacity-60 disabled:cursor-not-allowed"
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
                  disabled={field.disabled}
                  className="h-4 w-4 rounded border-border text-primary focus:ring-ring disabled:opacity-60 disabled:cursor-not-allowed"
                />
                <span className="text-sm text-muted-foreground">{field.label}</span>
              </label>
            ) : field.type === 'datetime-local' ? (
              <DateTimeInput
                id={field.name}
                name={field.name}
                value={values[field.name] ?? ''}
                onChange={(e) => onChange(field.name, e.target.value)}
                required={field.required}
                placeholder={field.placeholder}
                disabled={field.disabled}
              />
            ) : (
              <input
                id={field.name}
                name={field.name}
                type="text"
                value={values[field.name] ?? ''}
                onChange={(e) => onChange(field.name, e.target.value)}
                required={field.required}
                placeholder={field.placeholder}
                disabled={field.disabled}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring disabled:opacity-60 disabled:cursor-not-allowed"
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
        {pending && <Loader2 className="h-4 w-4 animate-spin" />}
        {pending ? 'Submitting…' : submitLabel}
      </button>
    </form>
  )
}
