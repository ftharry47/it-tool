import { AlertCircle } from 'lucide-react'

interface ErrorFallbackProps {
  error?: Error | null
  message?: string
  onRetry?: () => void
}

export function ErrorFallback({ error, message = 'Something went wrong.', onRetry }: ErrorFallbackProps) {
  return (
    <div role="alert" className="rounded-xl border border-destructive/50 bg-destructive/10 p-6 text-center text-destructive">
      <AlertCircle className="mx-auto mb-2 h-6 w-6" />
      <p className="font-medium">{message}</p>
      {error?.message && <p className="mt-1 text-sm">{error.message}</p>}
      {onRetry && (
        <button
          onClick={onRetry}
          className="mt-4 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
        >
          Retry
        </button>
      )}
    </div>
  )
}
