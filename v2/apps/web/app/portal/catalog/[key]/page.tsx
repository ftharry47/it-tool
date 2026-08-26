'use client'

import * as React from 'react'
import { useParams } from 'next/navigation'
import { api } from '@/lib/api'
import { IssueForm } from '@/components/issues/IssueForm'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import * as Icons from 'lucide-react'

export default function CatalogItemPage() {
  const { key } = useParams() as { key: string }
  const [item, setItem] = React.useState<any>(null)

  React.useEffect(() => {
    api(`/catalog/${encodeURIComponent(key)}`).then(setItem).catch(() => {})
  }, [key])

  if (!item) return <p className="text-muted-foreground">Loading...</p>

  const Icon = (Icons as any)[item.icon] || Icons.Circle

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <div className="flex items-center gap-3">
            <Icon className="h-8 w-8 text-primary" />
            <div>
              <CardTitle>{item.name}</CardTitle>
              <CardDescription>{item.description}</CardDescription>
            </div>
          </div>
        </CardHeader>
      </Card>
      <IssueForm defaultType={item.issueType} defaultCategory={item.category} />
    </div>
  )
}
