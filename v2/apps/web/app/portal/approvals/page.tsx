'use client'

import { ApprovalsList } from '@/components/approvals/ApprovalsList'

export default function ApprovalsPage() {
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Manager Approvals</h1>
      <p className="text-muted-foreground">Approve or reject requests from your team.</p>
      <ApprovalsList />
    </div>
  )
}
