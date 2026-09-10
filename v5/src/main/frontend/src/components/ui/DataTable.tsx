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
}

export function DataTable<T>({
  caption,
  columns,
  data,
  getRowKey,
  isLoading,
  emptyText = 'No items found.',
  onRowClick,
}: DataTableProps<T>) {
  return (
    <div
      className="overflow-x-auto scrollbar-themed rounded-xl border border-border bg-card p-4 shadow-sm"
    >
      <table className="w-full text-sm">
        {caption && <caption className="sr-only">{caption}</caption>}
        <thead>
          <tr className="border-b border-border text-left text-muted-foreground">
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
              <td colSpan={columns.length} className="py-8 text-center text-muted-foreground">
                Loading…
              </td>
            </tr>
          ) : data.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="py-8 text-center text-muted-foreground">
                {emptyText}
              </td>
            </tr>
          ) : (
            data.map((row) => (
              <tr
                key={getRowKey(row)}
                className={`border-b border-border/50 last:border-0 ${onRowClick ? 'cursor-pointer transition hover:bg-muted/50' : ''}`}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
              >
                {columns.map((col) => (
                  <td key={col.key} className="py-3 pr-4">
                    {col.render ? col.render(row) : String((row as Record<string, unknown>)[col.key] ?? '—')}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}
