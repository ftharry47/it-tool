'use client'

import { RequestsTable } from '@/components/issues/RequestsTable'

export default function RequestsPage() {
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">My Requests</h1>
      <p className="text-muted-foreground">Track all your incidents and service requests.</p>
      <RequestsTable />
    </div>
  )
}
