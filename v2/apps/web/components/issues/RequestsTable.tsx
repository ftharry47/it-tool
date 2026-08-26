'use client'

import * as React from 'react'
import Link from 'next/link'
import { api } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'

const statusColors: Record<string, string> = {
  SUBMITTED: 'secondary',
  IN_PROGRESS: 'default',
  ON_HOLD: 'outline',
  RESOLVED: 'secondary',
  CLOSED: 'outline',
}

export function RequestsTable() {
  const [issues, setIssues] = React.useState<any[]>([])

  React.useEffect(() => {
    api('/issues?mine=true')
      .then(setIssues)
      .catch(() => {})
  }, [])

  return (
    <Card>
      <CardHeader>
        <CardTitle>My Requests</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left text-muted-foreground">
                <th className="pb-2 font-medium">Ticket</th>
                <th className="pb-2 font-medium">Title</th>
                <th className="pb-2 font-medium">Type</th>
                <th className="pb-2 font-medium">Status</th>
                <th className="pb-2 font-medium">Created</th>
              </tr>
            </thead>
            <tbody>
              {issues.map((issue) => (
                <tr key={issue.id} className="border-b last:border-0">
                  <td className="py-3 font-mono text-xs">
                    <Link href={`/portal/requests/${issue.id}`} className="text-primary hover:underline">
                      {issue.ticketId}
                    </Link>
                  </td>
                  <td className="py-3">{issue.title}</td>
                  <td className="py-3">{issue.type}</td>
                  <td className="py-3">
                    <Badge variant={(statusColors[issue.status] as any) || 'default'}>{issue.status}</Badge>
                  </td>
                  <td className="py-3 text-muted-foreground">{new Date(issue.createdAt).toLocaleDateString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  )
}
