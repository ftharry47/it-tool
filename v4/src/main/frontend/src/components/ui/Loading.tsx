import { Loader2 } from 'lucide-react'

export function Loading({ message = 'Loading…' }: { message?: string }) {
  return (
    <div role="status" aria-live="polite" className="flex h-screen w-full items-center justify-center text-muted-foreground">
      <Loader2 className="mr-2 h-5 w-5 animate-spin" />
      {message}
    </div>
  )
}
