import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, FileText, HelpCircle, Search, User, X } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { Loading } from '../ui/Loading'

interface IncidentItem {
  id: string
  number: number | string
  title: string
  status: string
  priority?: string
}

interface ProblemItem {
  id: string
  number: string
  title: string
  status: string
}

interface ChangeItem {
  id: string
  number: string
  title: string
  status: string
}

interface KbItem {
  id: string
  number: string
  title: string
  category?: string
}

interface UserItem {
  id: string
  displayName: string
  email: string
}

interface GlobalSearchResponse {
  incidents: IncidentItem[]
  problems: ProblemItem[]
  changes: ChangeItem[]
  kb: KbItem[]
  users: UserItem[]
}

const ICONS: Record<string, React.ReactNode> = {
  incidents: <AlertTriangle className="h-4 w-4" />,
  problems: <HelpCircle className="h-4 w-4" />,
  changes: <FileText className="h-4 w-4" />,
  kb: <FileText className="h-4 w-4" />,
  users: <User className="h-4 w-4" />,
}

const LABELS: Record<string, string> = {
  incidents: 'Incidents',
  problems: 'Problems',
  changes: 'Changes',
  kb: 'Knowledge Base',
  users: 'Users',
}

function makeUrl(type: string, id: string): string {
  switch (type) {
    case 'incidents':
      return `/dashboard/incidents/${id}`
    case 'problems':
      return `/dashboard/problems/${id}`
    case 'changes':
      return `/dashboard/changes/${id}`
    case 'kb':
      return `/home/kb/${id}`
    case 'users':
      return '/admin/users'
    default:
      return '/'
  }
}

interface ResultItemProps {
  type: string
  title: string
  subtitle: string
  onClick: () => void
}

function ResultItem({ type, title, subtitle, onClick }: ResultItemProps) {
  return (
    <button
      onClick={onClick}
      className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-left hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary">
        {ICONS[type] ?? <Search className="h-4 w-4" />}
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{title}</p>
        <p className="truncate text-xs text-muted-foreground">{subtitle}</p>
      </div>
    </button>
  )
}

function ResultGroup({
  type,
  items,
  onSelect,
}: {
  type: string
  items: { id: string; title: string; subtitle: string }[]
  onSelect: (id: string) => void
}) {
  if (items.length === 0) return null
  return (
    <div className="space-y-1 px-2 pb-2">
      <h3 className="px-3 py-1 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
        {LABELS[type] ?? type}
      </h3>
      {items.map((item) => (
        <ResultItem
          key={`${type}-${item.id}`}
          type={type}
          title={item.title}
          subtitle={item.subtitle}
          onClick={() => onSelect(item.id)}
        />
      ))}
    </div>
  )
}

export function GlobalSearch() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setOpen((v) => !v)
      }
      if (e.key === 'Escape') {
        setOpen(false)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  useEffect(() => {
    if (!open) return
    const timer = setTimeout(() => setDebouncedQuery(query.trim()), 250)
    return () => clearTimeout(timer)
  }, [query, open])

  useEffect(() => {
    if (open) {
      setQuery('')
      setDebouncedQuery('')
    }
  }, [open])

  const searchQuery = useQuery<GlobalSearchResponse>({
    queryKey: ['global-search', debouncedQuery],
    queryFn: async () => {
      const res = await fetchWithToken(
        instance,
        account!,
        `/api/v1/search?q=${encodeURIComponent(debouncedQuery)}`
      )
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: open && debouncedQuery.length > 1,
    refetchOnWindowFocus: false,
    staleTime: 60_000,
  })

  const handleSelect = (type: string, id: string) => {
    setOpen(false)
    navigate(makeUrl(type, id))
  }

  const allResults: { type: string; items: { id: string; title: string; subtitle: string }[] }[] = [
    {
      type: 'incidents',
      items: (searchQuery.data?.incidents ?? []).map((i) => ({
        id: i.id,
        title: i.title,
        subtitle: `${i.number} • ${i.status}${i.priority ? ` • ${i.priority}` : ''}`,
      })),
    },
    {
      type: 'problems',
      items: (searchQuery.data?.problems ?? []).map((p) => ({
        id: p.id,
        title: p.title,
        subtitle: `${p.number} • ${p.status}`,
      })),
    },
    {
      type: 'changes',
      items: (searchQuery.data?.changes ?? []).map((c) => ({
        id: c.id,
        title: c.title,
        subtitle: `${c.number} • ${c.status}`,
      })),
    },
    {
      type: 'kb',
      items: (searchQuery.data?.kb ?? []).map((k) => ({
        id: k.id,
        title: k.title,
        subtitle: `KB ${k.number}${k.category ? ` • ${k.category}` : ''}`,
      })),
    },
    {
      type: 'users',
      items: (searchQuery.data?.users ?? []).map((u) => ({
        id: u.id,
        title: u.displayName,
        subtitle: u.email,
      })),
    },
  ]

  const totalResults = allResults.reduce((acc, g) => acc + g.items.length, 0)

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="inline-flex w-full max-w-md items-center justify-between gap-2 rounded-md border border-border bg-background px-3 py-2 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <span className="flex items-center gap-2">
          <Search className="h-4 w-4" />
          Search
        </span>
        <kbd className="hidden rounded border border-border bg-card px-1.5 text-xs font-medium sm:inline">
          ⌘K
        </kbd>
      </button>

      {open && (
        <div
          className="fixed inset-0 z-50 flex items-start justify-center bg-black/50 p-4 pt-24"
          onClick={() => setOpen(false)}
        >
          <div
            className="w-full max-w-2xl overflow-hidden rounded-xl border border-border bg-card shadow-lg"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center gap-2 border-b border-border px-4 py-3">
              <Search className="h-5 w-5 text-muted-foreground" />
              <input
                autoFocus
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search incidents, problems, changes, KB, users..."
                className="flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
              />
              <button
                onClick={() => setOpen(false)}
                className="rounded-md p-1 text-muted-foreground hover:bg-muted"
                aria-label="Close"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="max-h-[70vh] overflow-y-auto py-2">
              {debouncedQuery.length <= 1 && (
                <p className="px-4 py-6 text-center text-sm text-muted-foreground">
                  Type at least 2 characters to search.
                </p>
              )}
              {debouncedQuery.length > 1 && searchQuery.isLoading && (
                <div className="px-4 py-6">
                  <Loading />
                </div>
              )}
              {debouncedQuery.length > 1 && searchQuery.error && (
                <p className="px-4 py-6 text-center text-sm text-destructive">
                  Could not load search results.
                </p>
              )}
              {debouncedQuery.length > 1 && !searchQuery.isLoading && !searchQuery.error && totalResults === 0 && (
                <p className="px-4 py-6 text-center text-sm text-muted-foreground">
                  No results found for "{debouncedQuery}".
                </p>
              )}
              {allResults.map((group) => (
                <ResultGroup
                  key={group.type}
                  type={group.type}
                  items={group.items}
                  onSelect={(id) => handleSelect(group.type, id)}
                />
              ))}
            </div>

            <div className="flex items-center justify-between border-t border-border px-4 py-2 text-xs text-muted-foreground">
              <span>↑↓ to navigate</span>
              <span>↵ to select</span>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
