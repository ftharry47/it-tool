import { useEffect, useRef, useState } from 'react'
import { Check, ChevronDown } from 'lucide-react'

interface UserComboboxUser {
  id: string
  displayName: string
  email: string
}

interface UserComboboxProps {
  users: UserComboboxUser[]
  value: string
  onChange: (userId: string) => void
  placeholder?: string
  searchPlaceholder?: string
  emptyLabel?: string
  clearable?: boolean
  disabled?: boolean
}

export function UserCombobox({
  users,
  value,
  onChange,
  placeholder = 'None',
  searchPlaceholder = 'Search name or email…',
  emptyLabel = 'None',
  clearable = true,
  disabled = false,
}: UserComboboxProps) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const containerRef = useRef<HTMLDivElement>(null)

  const selected = users.find((u) => u.id === value)
  const q = query.trim().toLowerCase()
  const filtered = q
    ? users.filter(
        (u) =>
          (u.displayName ?? '').toLowerCase().includes(q) ||
          u.email.toLowerCase().includes(q)
      )
    : users

  useEffect(() => {
    if (!open) return
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    function handleEscape(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [open])

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        disabled={disabled}
        onClick={() => {
          if (disabled) return
          setOpen((o) => !o)
          setQuery('')
        }}
        aria-haspopup="listbox"
        aria-expanded={open}
        className="flex w-full items-center justify-between rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring disabled:opacity-50 disabled:cursor-not-allowed"
      >
        <span className={selected ? '' : 'text-muted-foreground'}>
          {selected ? `${selected.displayName || selected.email} (${selected.email})` : placeholder}
        </span>
        <ChevronDown className="h-4 w-4 text-muted-foreground" />
      </button>
      {open && (
        <div className="absolute z-20 mt-1 w-full rounded-md border border-border bg-card shadow-lg">
          <div className="border-b border-border p-2">
            <input
              autoFocus
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={searchPlaceholder}
              aria-label="Search users"
              className="w-full rounded-md border border-input bg-background px-2 py-1.5 text-sm outline-none focus:ring-2 focus:ring-ring"
            />
          </div>
          <ul role="listbox" className="max-h-56 overflow-y-auto py-1 scrollbar-themed">
            {clearable && (
              <li>
                <button
                  type="button"
                  role="option"
                  aria-selected={value === ''}
                  onClick={() => {
                    onChange('')
                    setOpen(false)
                  }}
                  className="flex w-full items-center justify-between px-3 py-2 text-left text-sm text-muted-foreground transition hover:bg-muted"
                >
                  {emptyLabel}
                  {value === '' && <Check className="h-4 w-4" />}
                </button>
              </li>
            )}
            {filtered.map((u) => (
              <li key={u.id}>
                <button
                  type="button"
                  role="option"
                  aria-selected={value === u.id}
                  onClick={() => {
                    onChange(u.id)
                    setOpen(false)
                  }}
                  className="flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm transition hover:bg-muted"
                >
                  <span className="min-w-0">
                    <span className="block truncate font-medium">{u.displayName || u.email}</span>
                    <span className="block truncate text-xs text-muted-foreground">{u.email}</span>
                  </span>
                  {value === u.id && <Check className="h-4 w-4 shrink-0" />}
                </button>
              </li>
            ))}
            {filtered.length === 0 && (
              <li className="px-3 py-2 text-sm text-muted-foreground">No users match "{query}".</li>
            )}
          </ul>
        </div>
      )}
    </div>
  )
}
