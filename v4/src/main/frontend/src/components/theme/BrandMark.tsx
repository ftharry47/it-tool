import { useState } from 'react'
import { HeartPulse } from 'lucide-react'
import { useThemeLogo } from './useThemeLogo'

/**
 * Shared brand lockup: theme-aware logo mark + "Azentro" wordmark.
 * Used by the login page and the app sidebar so sizing, spacing, and
 * typography stay identical everywhere.
 */
export function BrandMark() {
  const [logoError, setLogoError] = useState(false)
  const logoSrc = useThemeLogo()

  return (
    <div className="flex items-center gap-3">
      {logoError ? (
        <span className="flex h-12 w-12 shrink-0 items-center justify-center text-primary">
          <HeartPulse className="h-7 w-7" />
        </span>
      ) : (
        <img
          src={logoSrc}
          alt="Azentro logo"
          className="h-12 w-12 shrink-0 object-contain"
          onError={() => setLogoError(true)}
        />
      )}
      <span className="text-lg font-semibold leading-tight tracking-wide text-foreground">
        Azentro
      </span>
    </div>
  )
}
