// Shared phone validation: optional, but if provided must contain
// 10-15 digits (allowing +, spaces, dashes, parentheses as formatting).
export const PHONE_ERROR = 'Enter a valid phone number with at least 10 digits (e.g. +1 555-012-3456).'

export function isValidPhone(value: string | null | undefined): boolean {
  if (value == null || value.trim() === '') return true
  const trimmed = value.trim()
  if (!/^\+?[0-9\s\-().]+$/.test(trimmed)) return false
  const digits = trimmed.replace(/\D/g, '')
  return digits.length >= 10 && digits.length <= 15
}
