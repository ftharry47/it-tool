// Shared phone validation: required US 10-digit number. Formatting
// characters (spaces, dashes, parentheses, dots, leading +) are accepted
// and stripped; exactly 10 digits must remain.
export const PHONE_ERROR = 'Please enter a valid 10-digit phone number (e.g. 555-123-4567).'

export function isValidPhone(value: string | null | undefined): boolean {
  if (value == null || value.trim() === '') return false
  const trimmed = value.trim()
  if (!/^[0-9\s\-().+]+$/.test(trimmed)) return false
  return trimmed.replace(/\D/g, '').length === 10
}

// Normalizes to the plain 10-digit storage form: " (555) 123-4567" -> "5551234567"
export function normalizePhone(value: string): string {
  return value.replace(/\D/g, '')
}
