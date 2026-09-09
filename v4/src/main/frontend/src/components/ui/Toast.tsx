import { useEffect } from 'react'
import { AlertCircle, CheckCircle, X } from 'lucide-react'

export interface ToastItem {
  id: string
  type: 'success' | 'error'
  message: string
}

interface ToastStackProps {
  toasts: ToastItem[]
  onDismiss: (id: string) => void
}

const AUTO_DISMISS_MS: Record<ToastItem['type'], number> = {
  success: 4000,
  error: 6000,
}

function ToastCard({ toast, onDismiss }: { toast: ToastItem; onDismiss: () => void }) {
  useEffect(() => {
    const timer = setTimeout(onDismiss, AUTO_DISMISS_MS[toast.type])
    return () => clearTimeout(timer)
  }, [toast.id, toast.type, onDismiss])

  const Icon = toast.type === 'error' ? AlertCircle : CheckCircle

  return (
    <div
      role={toast.type === 'error' ? 'alert' : 'status'}
      className="animate-zoom-in-95 pointer-events-auto flex w-80 items-start gap-3 rounded-lg border border-border bg-card p-4 text-foreground shadow-lg"
    >
      <Icon
        className={`mt-0.5 h-5 w-5 shrink-0 ${toast.type === 'error' ? 'text-destructive' : 'text-primary'}`}
      />
      <span className="flex-1 text-sm">{toast.message}</span>
      <button
        onClick={onDismiss}
        aria-label="Dismiss notification"
        className="shrink-0 rounded-md p-0.5 text-muted-foreground transition hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  )
}

// Fixed top-right stack. Newest toast appears at the top; each card
// auto-dismisses (4s success / 6s error) and can be closed early via X.
export function ToastStack({ toasts, onDismiss }: ToastStackProps) {
  if (toasts.length === 0) return null
  return (
    <div className="pointer-events-none fixed right-4 top-4 z-50 flex flex-col gap-2">
      {toasts.map((toast) => (
        <ToastCard key={toast.id} toast={toast} onDismiss={() => onDismiss(toast.id)} />
      ))}
    </div>
  )
}
