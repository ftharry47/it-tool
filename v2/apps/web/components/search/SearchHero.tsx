'use client'

import * as React from 'react'
import { api } from '@/lib/api'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Search, BookOpen, LayoutGrid } from 'lucide-react'
import Link from 'next/link'

export function SearchHero() {
  const [q, setQ] = React.useState('')
  const [results, setResults] = React.useState<any[]>([])
  const [open, setOpen] = React.useState(false)

  React.useEffect(() => {
    if (!q.trim()) {
      setResults([])
      return
    }
    const id = setTimeout(async () => {
      try {
        const [catalog, kb] = await Promise.all([api(`/catalog?q=${encodeURIComponent(q)}`), api(`/knowledge/search?q=${encodeURIComponent(q)}`)])
        const mapped = [
          ...catalog.map((item: any) => ({ type: 'Catalog', title: item.name, to: `/portal/catalog?key=${item.key}`, category: item.category })),
          ...kb.map((article: any) => ({ type: 'KB', title: article.title, to: `/portal/kb/${article.id}`, category: article.category })),
        ]
        setResults(mapped)
        setOpen(true)
      } catch (e) {
        setResults([])
      }
    }, 300)
    return () => clearTimeout(id)
  }, [q])

  return (
    <div className="relative z-10">
      <div className="relative">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onFocus={() => q && setOpen(true)}
          placeholder="Search catalog items and knowledge articles..."
          className="h-14 pl-10 text-base shadow-lg"
        />
      </div>
      {open && results.length > 0 && (
        <div className="absolute mt-2 w-full rounded-lg border bg-background p-2 shadow-xl">
          <p className="px-2 py-1 text-xs font-medium text-muted-foreground">Federated Search Results</p>
          {results.map((r, i) => (
            <Link
              key={i}
              href={r.to}
              onClick={() => setOpen(false)}
              className="flex items-center justify-between rounded-md px-2 py-2 hover:bg-secondary"
            >
              <div className="flex items-center gap-2">
                {r.type === 'KB' ? <BookOpen className="h-4 w-4 text-primary" /> : <LayoutGrid className="h-4 w-4 text-primary" />}
                <span className="text-sm font-medium">{r.title}</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs text-muted-foreground">{r.category}</span>
                <Badge variant="outline">{r.type}</Badge>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
