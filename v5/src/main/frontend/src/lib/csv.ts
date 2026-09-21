/**
 * Client-side CSV export for report data already loaded in the UI.
 * Arrays of objects → header row + one row per entry; flat objects →
 * key/value rows. Nested values serialize as compact JSON.
 */
export function downloadCsv(filename: string, data: unknown) {
  const rows: string[][] = []
  if (Array.isArray(data) && data.length > 0 && typeof data[0] === 'object' && data[0] !== null) {
    const headers = Array.from(new Set(data.flatMap((r) => Object.keys(r as Record<string, unknown>))))
    rows.push(headers)
    for (const r of data as Record<string, unknown>[]) {
      rows.push(headers.map((h) => csvCell(r[h])))
    }
  } else if (data !== null && typeof data === 'object' && !Array.isArray(data)) {
    rows.push(['metric', 'value'])
    for (const [k, v] of Object.entries(data as Record<string, unknown>)) {
      rows.push([k, csvCell(v)])
    }
  } else {
    rows.push([csvCell(data)])
  }

  const csv = rows.map((r) => r.join(',')).join('\r\n')
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function csvCell(v: unknown): string {
  if (v === null || v === undefined) return ''
  const s = typeof v === 'object' ? JSON.stringify(v) : String(v)
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}
