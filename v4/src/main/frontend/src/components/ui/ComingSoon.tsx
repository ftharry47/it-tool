import { Construction } from 'lucide-react'

export function ComingSoon({ title }: { title: string }) {
  return (
    <div className="flex h-full min-h-[50vh] flex-col items-center justify-center gap-4 p-6 text-center text-muted-foreground">
      <Construction className="h-10 w-10" />
      <h2 className="text-xl font-semibold text-card-foreground">{title}</h2>
      <p className="max-w-md text-sm">This module is defined in the Phase 12 plan and will be implemented in an upcoming sub-phase.</p>
    </div>
  )
}
