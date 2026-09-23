import { useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { ArrowLeft, Upload, Loader2, AlertTriangle, CheckCircle2, FileSpreadsheet } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { useSmartBack } from '../../lib/useSmartBack'

interface NamedRef {
  id: string
  name: string
}

interface PreviewRow {
  rowNumber: number
  legacyTicketId: string | null
  title: string
  requester: string | null
  requesterMatched: boolean
  locationName: string | null
  locationMatched: boolean
  locationMatch: 'NONE' | 'EXACT' | 'FUZZY' | 'MANUAL'
  locationResolvedName: string | null
  categoryName: string | null
  categoryMatched: boolean
  priorityName: string | null
  markedCritical: boolean
  assigneeName: string | null
  assigneeMatched: boolean
  assigneeMatch: 'NONE' | 'EXACT' | 'FUZZY' | 'MANUAL'
  assigneeResolvedName: string | null
  status: string
  createdAt: string | null
  resolvedAt: string | null
  warnings: string[]
  error: string | null
}

interface ImportPreview {
  totalRows: number
  importable: number
  skipped: number
  rows: PreviewRow[]
  availableLocations: NamedRef[]
  availableAssignees: NamedRef[]
}

interface ImportResult {
  imported: number
  skipped: number
  skippedRows: { rowNumber: number; reason: string }[]
}

type MatchKind = 'NONE' | 'EXACT' | 'FUZZY' | 'MANUAL'

/**
 * Preview-cell for a legacy name → system reference mapping. Unmatched and
 * fuzzy values get an inline dropdown of real options; since corrections are
 * keyed by the raw cell text, one pick fixes every row sharing that value.
 */
function MappingCell({
  raw,
  match,
  resolvedName,
  options,
  correction,
  sameCount,
  onCorrect,
}: {
  raw: string | null
  match: MatchKind
  resolvedName: string | null
  options: NamedRef[]
  correction: string | undefined
  sameCount: number
  onCorrect: (value: string) => void
}) {
  if (!raw) return <span>—</span>
  const effectiveMatch: MatchKind = correction ? 'MANUAL' : match
  const effectiveName = correction
    ? (options.find((o) => o.id === correction)?.name ?? resolvedName)
    : resolvedName
  return (
    <div>
      <span>{effectiveMatch === 'NONE' ? raw : (effectiveName ?? raw)}</span>
      {effectiveMatch === 'NONE' && <span className="ml-1 text-xs text-amber-600">(unmatched)</span>}
      {effectiveMatch === 'FUZZY' && <span className="ml-1 text-xs text-amber-600">(auto-matched)</span>}
      {effectiveMatch === 'MANUAL' && <span className="ml-1 text-xs text-emerald-600">(mapped)</span>}
      {effectiveMatch !== 'EXACT' && (
        <select
          value={correction ?? ''}
          onChange={(e) => onCorrect(e.target.value)}
          className="mt-1 block w-full max-w-56 rounded-md border border-input bg-background px-2 py-1 text-xs outline-none focus:ring-2 focus:ring-ring"
        >
          <option value="">— Leave empty —</option>
          {options.map((o) => (
            <option key={o.id} value={o.id}>{o.name}</option>
          ))}
        </select>
      )}
      {sameCount > 1 && effectiveMatch !== 'EXACT' && (
        <p className="mt-0.5 text-xs text-muted-foreground">
          Applies to all {sameCount} rows with “{raw}”
        </p>
      )}
    </div>
  )
}

export function ImportTickets() {
  const smartBack = useSmartBack('/admin')
  const { instance, accounts } = useMsal()
  const account = accounts[0]

  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // Manual corrections keyed by the raw cell text from the file.
  const [locationCorrections, setLocationCorrections] = useState<Record<string, string>>({})
  const [assigneeCorrections, setAssigneeCorrections] = useState<Record<string, string>>({})

  const postFile = async (endpoint: string, overrides?: object) => {
    const formData = new FormData()
    formData.append('file', file!)
    if (overrides) formData.append('overrides', JSON.stringify(overrides))
    const res = await fetchWithToken(instance, account, endpoint, { method: 'POST', body: formData })
    const body = await res.json().catch(() => ({}))
    if (!res.ok) throw new Error(body.error ?? `HTTP ${res.status}`)
    return body
  }

  const handlePreview = async () => {
    if (!file) return
    setBusy(true)
    setError(null)
    setPreview(null)
    setResult(null)
    try {
      setLocationCorrections({})
      setAssigneeCorrections({})
      setPreview(await postFile('/api/v1/admin/import/incidents/preview'))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const handleCommit = async () => {
    if (!file) return
    setBusy(true)
    setError(null)
    try {
      setResult(await postFile('/api/v1/admin/import/incidents/commit', {
        location: locationCorrections,
        assignee: assigneeCorrections,
      }))
      setPreview(null)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex items-center gap-4">
          <button
            onClick={smartBack}
            className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </button>
          <h1 className="text-2xl font-semibold tracking-tight">Import Historical Tickets</h1>
        </div>

        <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
          <p className="text-sm text-muted-foreground">
            Upload a legacy <code>.xlsx</code> export with columns: Ticket ID, Created Date, Name, Email, Phone,
            Location, Issue Type, Impact Area, Description, Status, Priority, Marked Critical, Assigned To,
            Escalation Level, Resolved By, Resolved Date. All imported tickets become CLOSED legacy records and
            are excluded from SLA tracking.
          </p>
          <div className="mt-4 flex items-center gap-3">
            <input
              type="file"
              accept=".xlsx"
              onChange={(e) => { setFile(e.target.files?.[0] ?? null); setPreview(null); setResult(null); setError(null) }}
              className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            />
            <button
              onClick={handlePreview}
              disabled={!file || busy}
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              {busy && !preview ? <Loader2 className="h-4 w-4 animate-spin" /> : <FileSpreadsheet className="h-4 w-4" />}
              Preview Import
            </button>
          </div>
          {error && <p className="mt-3 text-sm text-destructive">{error}</p>}
        </div>

        {preview && (() => {
          // Rows still unresolved per raw value (drives "applies to all N" hint).
          const countUnmatched = (key: 'location' | 'assignee') => {
            const counts = new Map<string, number>()
            for (const r of preview.rows) {
              const raw = key === 'location' ? r.locationName : r.assigneeName
              const m = key === 'location' ? r.locationMatch : r.assigneeMatch
              const corrected = key === 'location' ? locationCorrections : assigneeCorrections
              if (raw && m === 'NONE' && !corrected[raw]) counts.set(raw, (counts.get(raw) ?? 0) + 1)
            }
            return counts
          }
          const unmatchedLocations = countUnmatched('location')
          const unmatchedAssignees = countUnmatched('assignee')
          const unresolved = unmatchedLocations.size + unmatchedAssignees.size
          // Suppress a stale "unmatched" warning once a correction exists for that raw value.
          const warningsFor = (r: PreviewRow) =>
            r.warnings.filter((w) => {
              if (r.locationName && locationCorrections[r.locationName] && w.startsWith(`Location '${r.locationName}'`)) return false
              if (r.assigneeName && assigneeCorrections[r.assigneeName] && w.startsWith(`Assignee '${r.assigneeName}'`)) return false
              return true
            })
          return (
          <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center gap-4">
                <h2 className="text-base font-semibold">Preview — {preview.importable} of {preview.totalRows} rows will import</h2>
                {preview.skipped > 0 && (
                  <span className="text-sm text-destructive">{preview.skipped} row(s) will be skipped</span>
                )}
                {unresolved > 0 && (
                  <span className="rounded-full bg-amber-500/10 px-2.5 py-0.5 text-xs font-medium text-amber-600">
                    {unresolved} unmatched value{unresolved === 1 ? '' : 's'} — pick a mapping below
                  </span>
                )}
              </div>
              <button
                onClick={handleCommit}
                disabled={busy || preview.importable === 0}
                className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
                Confirm Import
              </button>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-border text-xs uppercase tracking-wide text-muted-foreground">
                    <th className="px-3 py-2">Row</th>
                    <th className="px-3 py-2">Ticket ID</th>
                    <th className="px-3 py-2">Title</th>
                    <th className="px-3 py-2">Requester</th>
                    <th className="px-3 py-2">Location</th>
                    <th className="px-3 py-2">Category</th>
                    <th className="px-3 py-2">Priority</th>
                    <th className="px-3 py-2">Assignee</th>
                    <th className="px-3 py-2">Notes</th>
                  </tr>
                </thead>
                <tbody>
                  {preview.rows.map((row) => (
                    <tr key={row.rowNumber} className={`border-b border-border ${row.error ? 'bg-destructive/10' : ''}`}>
                      <td className="px-3 py-2">{row.rowNumber}</td>
                      <td className="px-3 py-2 font-mono text-xs">{row.legacyTicketId ?? '—'}</td>
                      <td className="max-w-48 truncate px-3 py-2">{row.title}</td>
                      <td className="px-3 py-2">
                        {row.requester ?? '—'}
                        {!row.requesterMatched && <span className="ml-1 text-xs text-amber-600">(placeholder)</span>}
                      </td>
                      <td className="px-3 py-2">
                        <MappingCell
                          raw={row.locationName}
                          match={row.locationMatch}
                          resolvedName={row.locationResolvedName}
                          options={preview.availableLocations}
                          correction={row.locationName ? locationCorrections[row.locationName] : undefined}
                          sameCount={unmatchedLocations.get(row.locationName ?? '') ?? 0}
                          onCorrect={(v) => setLocationCorrections((prev) => {
                            const next = { ...prev }
                            if (row.locationName) {
                              if (v) next[row.locationName] = v
                              else delete next[row.locationName]
                            }
                            return next
                          })}
                        />
                      </td>
                      <td className="px-3 py-2">{row.categoryName ?? '—'}</td>
                      <td className="px-3 py-2">
                        {row.priorityName ?? '—'}
                        {row.markedCritical && <span className="ml-1 text-xs font-medium text-destructive">★</span>}
                      </td>
                      <td className="px-3 py-2">
                        <MappingCell
                          raw={row.assigneeName}
                          match={row.assigneeMatch}
                          resolvedName={row.assigneeResolvedName}
                          options={preview.availableAssignees}
                          correction={row.assigneeName ? assigneeCorrections[row.assigneeName] : undefined}
                          sameCount={unmatchedAssignees.get(row.assigneeName ?? '') ?? 0}
                          onCorrect={(v) => setAssigneeCorrections((prev) => {
                            const next = { ...prev }
                            if (row.assigneeName) {
                              if (v) next[row.assigneeName] = v
                              else delete next[row.assigneeName]
                            }
                            return next
                          })}
                        />
                      </td>
                      <td className="px-3 py-2 text-xs">
                        {row.error ? (
                          <span className="flex items-center gap-1 text-destructive"><AlertTriangle className="h-3 w-3" />{row.error}</span>
                        ) : (
                          warningsFor(row).map((w, i) => (
                            <span key={i} className="mb-0.5 flex items-center gap-1 text-amber-600"><AlertTriangle className="h-3 w-3 shrink-0" />{w}</span>
                          ))
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          )
        })()}

        {result && (
          <div className="rounded-xl border border-border bg-card p-6 shadow-sm">
            <h2 className="flex items-center gap-2 text-base font-semibold">
              <CheckCircle2 className="h-5 w-5 text-emerald-600" />
              Import complete — {result.imported} imported, {result.skipped} skipped
            </h2>
            {result.skippedRows.length > 0 && (
              <ul className="mt-3 space-y-1 text-sm text-muted-foreground">
                {result.skippedRows.map((s) => (
                  <li key={s.rowNumber}>Row {s.rowNumber}: {s.reason}</li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
