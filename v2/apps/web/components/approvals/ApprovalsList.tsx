'use client'

import * as React from 'react'
import { api } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'

export function ApprovalsList() {
  const [approvals, setApprovals] = React.useState<any[]>([])

  const load = () => {
    api('/approvals/pending')
      .then(setApprovals)
      .catch(() => {})
  }

  React.useEffect(() => {
    load()
  }, [])

  const decide = async (id: string, status: 'APPROVED' | 'REJECTED') => {
    await api(`/approvals/${id}/decide`, {
      method: 'POST',
      body: JSON.stringify({ status }),
    })
    load()
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Pending Approvals</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {approvals.length === 0 && <p className="text-sm text-muted-foreground">No pending approvals.</p>}
        {approvals.map((a) => (
          <div key={a.id} className="flex items-start justify-between rounded-lg border p-3">
            <div>
              <p className="font-medium">{a.issue.title}</p>
              <p className="text-xs text-muted-foreground">Requested by {a.issue.requester?.name}</p>
              <Badge variant="outline" className="mt-1">
                {a.issue.type}
              </Badge>
            </div>
            <div className="flex gap-2">
              <Button size="sm" onClick={() => decide(a.id, 'APPROVED')}>
                Approve
              </Button>
              <Button size="sm" variant="outline" onClick={() => decide(a.id, 'REJECTED')}>
                Reject
              </Button>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}
