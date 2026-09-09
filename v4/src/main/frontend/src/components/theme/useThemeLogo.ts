import { useTheme } from './ThemeProvider'

/**
 * Returns the logo asset matching the currently resolved theme.
 * Light theme -> logo-light-square.png, dark theme -> logo-dark-square.png.
 * The square variants are trimmed to the mark's bounding box so the logo
 * fills the image element instead of sitting inside large transparent margins.
 */
export function useThemeLogo(): string {
  const { resolvedTheme } = useTheme()
  return resolvedTheme === 'dark' ? '/logo-dark-square.png' : '/logo-light-square.png'
}
