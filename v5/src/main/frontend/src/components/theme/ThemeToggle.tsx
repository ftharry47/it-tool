import { Moon, Sun } from 'lucide-react'
import { useTheme } from './ThemeProvider'

interface ThemeToggleProps {
  className?: string
}

export function ThemeToggle({ className }: ThemeToggleProps) {
  const { theme, setTheme } = useTheme()

  function isDark() {
    if (theme === 'system') {
      return window.matchMedia('(prefers-color-scheme: dark)').matches
    }
    return theme === 'dark'
  }

  const dark = isDark()

  return (
    <button
      onClick={() => setTheme(dark ? 'light' : 'dark')}
      className={`flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm transition hover:bg-muted ${className ?? ''}`}
      aria-label="Toggle theme"
    >
      {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
      <span>{dark ? 'Light mode' : 'Dark mode'}</span>
    </button>
  )
}
