'use client'

import * as React from 'react'
import Link from 'next/link'
import { api } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import * as Icons from 'lucide-react'

export function CatalogGrid({ limit, category }: { limit?: number; category?: string }) {
  const [items, setItems] = React.useState<any[]>([])

  React.useEffect(() => {
    const url = category ? `/catalog?category=${encodeURIComponent(category)}` : '/catalog'
    api(url)
      .then((data: any[]) => {
        setItems(limit ? data.slice(0, limit) : data)
      })
      .catch(() => {})
  }, [category, limit])

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {items.map((item) => {
        const Icon = (Icons as any)[item.icon] || Icons.Circle
        return (
          <Link key={item.id} href={`/portal/catalog/${item.key}`}>
            <Card className="h-full transition hover:border-primary">
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <Icon className="h-6 w-6 text-primary" />
                  <Badge variant="secondary">{item.issueType}</Badge>
                </div>
                <CardTitle className="text-base">{item.name}</CardTitle>
                <CardDescription className="line-clamp-2">{item.description}</CardDescription>
              </CardHeader>
              <CardContent>
                <Badge variant="outline">{item.category}</Badge>
              </CardContent>
            </Card>
          </Link>
        )
      })}
    </div>
  )
}
