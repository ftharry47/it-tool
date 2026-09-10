import type { ReactNode } from 'react'

interface Column<T> {
  key: string
  header: string
  render?: (row: T) => ReactNode
}

interface DataTableProps<T> {
  caption?: string
  columns: Column<T>[]
  data: T[]
  getRowKey: (row: T) => string
  isLoading?: boolean
  emptyText?: string
  onRowClick?: (row: T) => void
  selectable?: boolean
  selectedIds?: Set<string>
  onSelectionChange?: (ids: Set<string>) => void
}

export function DataTable<T>({
  caption,
  columns,
  data,
  getRowKey,
  isLoading,
  emptyText = 'No items found.',
  onRowClick,
  selectable,
  selectedIds = new Set(),
  onSelectionChange,
}: DataTableProps<T>) {
  const allSelected = data.length > 0 && data.every((row) => selectedIds.has(getRowKey(row)))
  const someSelected = data.some((row) => selectedIds.has(getRowKey(row))) && !allSelected

  const toggleAll = () => {
    if (!onSelectionChange) return
    const next = new Set(selectedIds)
    if (allSelected) {
      data.forEach((row) => next.delete(getRowKey(row)))
    } else {
      data.forEach((row) => next.add(getRowKey(row)))
    }
    onSelectionChange(next)
  }

  const toggleOne = (e: React.MouseEvent, id: string) => {
    e.stopPropagation()
    if (!onSelectionChange) return
    const next = new Set(selectedIds)
    if (next.has(id)) {
      next.delete(id)
    } else {
      next.add(id)
    }
    onSelectionChange(next)
  }

  return (
    <div
      className="overflow-x-auto scrollbar-themed rounded-xl border border-border bg-card p-4 shadow-sm"
    >
      <table className="w-full text-sm">
        {caption && <caption className="sr-only">{caption}</caption>}
        <thead>
          <tr className="border-b border-border text-left text-muted-foreground">
            {selectable && (
              <th className="w-10 py-2 pr-2">
                <input
                  type="checkbox"
                  checked={allSelected}
                  ref={(el) => {
                    if (el) el.indeterminate = someSelected
                  }}
                  onChange={toggleAll}
                  aria-label="Select all rows"
                  className="h-4 w-4 rounded border-border text-primary focus:ring-ring"
                />
              </th>
            )}
            {columns.map((col) => (
              <th key={col.key} scope="col" className="py-2 pr-4 font-medium">
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={columns.length + (selectable ? 1 : 0)} className="py-8 text-center text-muted-foreground">
                Loading…
              </td>
            </tr>
          ) : data.length === 0 ? (
            <tr>
              <td colSpan={columns.length + (selectable ? 1 : 0)} className="py-8 text-center text-muted-foreground">
                {emptyText}
              </td>
            </tr>
          ) : (
            data.map((row) => {
              const id = getRowKey(row)
              return (
                <tr
                  key={id}
                  className={`border-b border-border/50 last:border-0 ${onRowClick ? 'cursor-pointer transition hover:bg-muted/50' : ''}`}
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                >
                  {selectable && (
                    <td className="py-3 pr-2" onClick={(e) => e.stopPropagation()}>
                      <input
                        type="checkbox"
                        checked={selectedIds.has(id)}
                        onChange={(e) => toggleOne(e as any, id)}
                        aria-label={`Select row ${id}`}
                        className="h-4 w-4 rounded border-border text-primary focus:ring-ring"
                      />
                    </td>
                  )}
                  {columns.map((col) => (
                    <td key={col.key} className="py-3 pr-4">
                      {col.render ? col.render(row) : String((row as Record<string, unknown>)[col.key] ?? '—')}
                    </td>
                  ))}
                </tr>
              )
            })
          )}
        </tbody>
      </table>
    </div>
  )
}
