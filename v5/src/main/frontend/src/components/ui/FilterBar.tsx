import { useCallback, useState } from 'react'
import { X } from 'lucide-react'

export interface FilterOption {
  value: string
  label: string
}

interface FilterSelectProps {
  label: string
  value: string
  options: FilterOption[]
  onChange: (value: string) => void
}

export function FilterSelect({ label, value, options, onChange }: FilterSelectProps) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      aria-label={`Filter by ${label}`}
      className="rounded-md border border-input bg-background px-3 py-1.5 text-sm outline-none focus:ring-2 focus:ring-ring"
    >
      <option value="">{label}: All</option>
      {options.map((o) => (
        <option key={o.value} value={o.value}>{o.label}</option>
      ))}
    </select>
  )
}

interface FilterBarProps {
  activeCount: number
  onClear: () => void
  children: React.ReactNode
}

export function FilterBar({ activeCount, onClear, children }: FilterBarProps) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      {children}
      {activeCount > 0 && (
        <button
          type="button"
          onClick={onClear}
          className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm font-medium text-muted-foreground transition hover:bg-muted hover:text-foreground"
        >
          <X className="h-3.5 w-3.5" />
          Clear filters
        </button>
      )}
    </div>
  )
}

/**
 * useSessionFilters — filter state persisted to sessionStorage so navigating
 * away and back within the session keeps the selection. `key` must be unique
 * per list (e.g. 'incident-filters', 'sr-filters').
 */
export function useSessionFilters<T extends Record<string, string>>(key: string, initial: T) {
  const [filters, setFilters] = useState<T>(() => {
    try {
      const stored = sessionStorage.getItem(key)
      if (stored) return { ...initial, ...JSON.parse(stored) }
    } catch { /* ignore malformed storage */ }
    return initial
  })

  const setFilter = useCallback(
    (field: keyof T, value: string) => {
      setFilters((prev) => {
        const next = { ...prev, [field]: value }
        try {
          sessionStorage.setItem(key, JSON.stringify(next))
        } catch { /* storage full/blocked — non-fatal */ }
        return next
      })
    },
    [key]
  )

  const clearFilters = useCallback(() => {
    setFilters(initial)
    try {
      sessionStorage.removeItem(key)
    } catch { /* non-fatal */ }
  }, [key, initial])

  const activeCount = Object.values(filters).filter((v) => v !== '').length

  return { filters, setFilter, clearFilters, activeCount }
}

/** Format an enum name like WAITING_ON_CUSTOMER → "Waiting On Customer" for filter labels. */
export function enumLabel(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ')
}
