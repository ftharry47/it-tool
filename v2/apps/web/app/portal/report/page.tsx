'use client'

import { IssueForm } from '@/components/issues/IssueForm'

export default function ReportPage() {
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Report an Issue</h1>
      <p className="text-muted-foreground">Submit an incident or service request.</p>
      <IssueForm />
    </div>
  )
}
