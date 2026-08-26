'use client'

import { KbBrowser } from '@/components/kb/KbBrowser'

export default function KbPage() {
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Knowledge Base</h1>
      <p className="text-muted-foreground">Find answers and self-service help.</p>
      <KbBrowser />
    </div>
  )
}
