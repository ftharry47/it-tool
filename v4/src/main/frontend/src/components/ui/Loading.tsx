import { Loader2 } from 'lucide-react'

export function Loading({ message = 'Loading…', compact = false }: { message?: string; compact?: boolean }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className={`flex w-full items-center justify-center text-muted-foreground ${compact ? 'h-48' : 'h-screen'}`}
    >
      <Loader2 className="mr-2 h-5 w-5 animate-spin" />
      {message}
    </div>
  )
}
