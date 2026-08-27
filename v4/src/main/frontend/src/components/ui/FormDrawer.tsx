import type { ReactNode } from 'react'
import { X } from 'lucide-react'

interface FormDrawerProps {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
}

export function FormDrawer({ open, title, onClose, children }: FormDrawerProps) {
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" role="dialog" aria-modal="true" aria-labelledby="drawer-title">
      <div className="h-full w-full max-w-md overflow-y-auto bg-card p-6 shadow-xl">
        <div className="mb-6 flex items-center justify-between">
          <h2 id="drawer-title" className="text-lg font-semibold">{title}</h2>
          <button
            onClick={onClose}
            className="rounded-md p-1 hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label="Close"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
