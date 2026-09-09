/**
 * Shared date/time formatting for the whole frontend.
 *
 * All timestamps from the backend are UTC (timestamptz / ISO-8601 with offset).
 * Everything the user sees is rendered in US Eastern Time (America/New_York),
 * which auto-adjusts for EST/EDT via the Intl timezone database.
 *
 * Dates:   MM/DD/YYYY            -> formatDate(iso)
 * Date+time: MM/DD/YYYY, h:mm AM/PM -> formatDateTime(iso)
 *
 * Use these everywhere instead of toLocaleDateString()/toLocaleString() so the
 * display never drifts to the viewer's browser locale or timezone.
 */

const TIME_ZONE = 'America/New_York'

const DATE_FMT = new Intl.DateTimeFormat('en-US', {
  timeZone: TIME_ZONE,
  month: '2-digit',
  day: '2-digit',
  year: 'numeric',
})

const DATE_TIME_FMT = new Intl.DateTimeFormat('en-US', {
  timeZone: TIME_ZONE,
  month: '2-digit',
  day: '2-digit',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
})

const WEEKDAY_FMT = new Intl.DateTimeFormat('en-US', {
  timeZone: TIME_ZONE,
  weekday: 'short',
  month: 'short',
  day: 'numeric',
})

function parse(iso: string | null | undefined): Date | null {
  if (!iso) return null
  const d = new Date(iso)
  return isNaN(d.getTime()) ? null : d
}

/** "MM/DD/YYYY" in America/New_York. Returns '—' for null/invalid input. */
export function formatDate(iso: string | null | undefined): string {
  const d = parse(iso)
  return d ? DATE_FMT.format(d) : '—'
}

/** "MM/DD/YYYY, h:mm AM/PM" in America/New_York. Returns '—' for null/invalid input. */
export function formatDateTime(iso: string | null | undefined): string {
  const d = parse(iso)
  return d ? DATE_TIME_FMT.format(d) : '—'
}

/** "Mon, Sep 7" style label in America/New_York (calendar day headers). */
export function formatWeekdayDate(iso: string | Date | null | undefined): string {
  const d = iso instanceof Date ? iso : parse(iso)
  return d && !isNaN(d.getTime()) ? WEEKDAY_FMT.format(d) : '—'
}

/**
 * Converts a UTC ISO timestamp to the "YYYY-MM-DDTHH:mm" wall-clock value
 * expected by <input type="datetime-local">, expressed in America/New_York so
 * the user sees and edits Eastern time.
 */
export function toEasternInputValue(iso: string | null | undefined): string {
  const d = parse(iso)
  if (!d) return ''
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(d)
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? ''
  const hour = get('hour') === '24' ? '00' : get('hour')
  return `${get('year')}-${get('month')}-${get('day')}T${hour}:${get('minute')}`
}

/**
 * Inverse of toEasternInputValue: interprets a "YYYY-MM-DDTHH:mm"
 * datetime-local value as America/New_York wall time and returns a UTC ISO
 * string for the API. Returns null for empty/invalid input.
 */
export function easternInputToIso(value: string | null | undefined): string | null {
  if (!value) return null
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value)
  if (!m) return null
  const [, y, mo, d, h, mi] = m
  // Find the UTC instant whose Eastern wall time matches the input by
  // iterating the UTC offset guess (handles EST/EDT transitions).
  const guess = Date.UTC(+y, +mo - 1, +d, +h, +mi)
  for (const offsetGuess of [guess, guess - 5 * 3600_000, guess - 4 * 3600_000]) {
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone: TIME_ZONE,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).formatToParts(new Date(offsetGuess))
    const get = (type: string) => parts.find((p) => p.type === type)?.value ?? ''
    const hour = get('hour') === '24' ? '00' : get('hour')
    if (`${get('year')}-${get('month')}-${get('day')}T${hour}:${get('minute')}` === value) {
      return new Date(offsetGuess).toISOString()
    }
  }
  // Fallback: treat as UTC-5 (EST) — correct for the majority of the year.
  return new Date(guess + 5 * 3600_000).toISOString()
}
