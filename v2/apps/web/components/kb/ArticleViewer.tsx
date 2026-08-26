'use client'

import * as React from 'react'
import { api } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'

export function ArticleViewer({ id }: { id: string }) {
  const [article, setArticle] = React.useState<any>(null)

  React.useEffect(() => {
    api(`/knowledge/${id}`).then(setArticle).catch(() => {})
  }, [id])

  if (!article) return <p className="text-muted-foreground">Loading...</p>

  return (
    <Card>
      <CardHeader>
        <CardTitle>{article.title}</CardTitle>
        <CardDescription>
          <Badge variant="outline">{article.category}</Badge>
        </CardDescription>
      </CardHeader>
      <CardContent>
        <p className="whitespace-pre-wrap leading-relaxed">{article.content}</p>
      </CardContent>
    </Card>
  )
}
