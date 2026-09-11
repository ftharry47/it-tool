import { X } from 'lucide-react'

interface BulkActionToolbarProps {
  selectedCount: number
  view: 'active' | 'deleted'
  onCancel: () => void
  onDelete: () => void
  onRestore: () => void
}

export function BulkActionToolbar({ selectedCount, view, onCancel, onDelete, onRestore }: BulkActionToolbarProps) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border bg-card p-3">
      <button
        type="button"
        onClick={onCancel}
        className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted"
      >
        <X className="h-3.5 w-3.5" />
        Cancel selection
      </button>

      <span className="text-sm text-muted-foreground">
        {selectedCount === 0 ? 'No items selected' : `${selectedCount} selected`}
      </span>

      {view === 'active' ? (
        <button
          type="button"
          disabled={selectedCount === 0}
          onClick={onDelete}
          className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-1.5 text-sm text-destructive transition hover:bg-destructive/20 disabled:opacity-50"
        >
          Delete Selected
        </button>
      ) : (
        <button
          type="button"
          disabled={selectedCount === 0}
          onClick={onRestore}
          className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50"
        >
          Restore Selected
        </button>
      )}
    </div>
  )
}
