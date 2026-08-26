'use client'

import * as React from 'react'
import Link from 'next/link'
import { api } from '@/lib/api'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'

export function KbBrowser() {
  const [q, setQ] = React.useState('')
  const [articles, setArticles] = React.useState<any[]>([])

  React.useEffect(() => {
    const id = setTimeout(() => {
      const url = q ? `/knowledge?q=${encodeURIComponent(q)}` : '/knowledge'
      api(url)
        .then((data: any[]) => setArticles(data))
        .catch(() => {})
    }, 300)
    return () => clearTimeout(id)
  }, [q])

  return (
    <div className="space-y-4">
      <Input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Search knowledge base..."
      />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {articles.map((article) => (
          <Link key={article.id} href={`/portal/kb/${article.id}`}>
            <Card className="h-full transition hover:border-primary">
              <CardHeader className="pb-2">
                <CardTitle className="text-base">{article.title}</CardTitle>
                <CardDescription className="line-clamp-2">{article.content.slice(0, 80)}...</CardDescription>
              </CardHeader>
              <CardContent>
                <Badge variant="outline">{article.category}</Badge>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  )
}
