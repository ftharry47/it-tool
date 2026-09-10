import { useState, type ReactNode } from 'react'
import { X } from 'lucide-react'
import { ConfirmDialog } from './ConfirmDialog'

interface FormDrawerProps {
  open: boolean
  title: string
  onClose: () => void
  /** When true, closing via the X button asks for confirmation first. */
  dirty?: boolean
  children: ReactNode
}

export function FormDrawer({ open, title, onClose, dirty = false, children }: FormDrawerProps) {
  const [confirmClose, setConfirmClose] = useState(false)

  if (!open) return null

  const handleClose = () => {
    if (dirty) {
      setConfirmClose(true)
    } else {
      onClose()
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" role="dialog" aria-modal="true" aria-labelledby="drawer-title">
      <div className="h-full w-full max-w-md overflow-y-auto bg-card p-6 shadow-xl scrollbar-themed">
        <div className="mb-6 flex items-center justify-between">
          <h2 id="drawer-title" className="text-lg font-semibold">{title}</h2>
          <button
            onClick={handleClose}
            className="rounded-md p-1 hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label="Close"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        {children}
      </div>
      <ConfirmDialog
        open={confirmClose}
        title="Discard unsaved changes?"
        description="You have unsaved changes that will be lost if you close this form."
        confirmLabel="Discard"
        cancelLabel="Cancel"
        destructive
        onConfirm={() => {
          setConfirmClose(false)
          onClose()
        }}
        onCancel={() => setConfirmClose(false)}
      />
    </div>
  )
}
