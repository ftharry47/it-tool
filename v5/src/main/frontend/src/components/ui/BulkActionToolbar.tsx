interface BulkActionToolbarProps {
  selectedCount: number
  showDeleted: boolean
  onToggleShowDeleted: () => void
  onDelete: () => void
  onRestore: () => void
}

export function BulkActionToolbar({ selectedCount, showDeleted, onToggleShowDeleted, onDelete, onRestore }: BulkActionToolbarProps) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border bg-card p-3">
      <div className="flex items-center gap-2">
        <span className="text-sm text-muted-foreground">
          {selectedCount === 0 ? 'No items selected' : `${selectedCount} selected`}
        </span>
        <button
          type="button"
          disabled={selectedCount === 0}
          onClick={onDelete}
          className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-1 text-sm text-destructive hover:bg-destructive/20 disabled:opacity-50"
        >
          Delete Selected
        </button>
        <button
          type="button"
          disabled={selectedCount === 0}
          onClick={onRestore}
          className="rounded-md border border-border px-3 py-1 text-sm hover:bg-muted disabled:opacity-50"
        >
          Restore Selected
        </button>
      </div>
      <label className="inline-flex items-center gap-2 text-sm text-muted-foreground">
        <input
          type="checkbox"
          checked={showDeleted}
          onChange={onToggleShowDeleted}
          className="h-4 w-4 rounded border-border text-primary focus:ring-ring"
        />
        Show deleted
      </label>
    </div>
  )
}
