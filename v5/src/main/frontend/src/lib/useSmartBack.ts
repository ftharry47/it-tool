import { useNavigate } from 'react-router-dom'

/**
 * Smart back: behaves like real browser back when the user navigated here
 * from another in-app page, but falls back to a sensible parent route when
 * there's no previous in-app entry (fresh tab, notification/email link).
 *
 * Detection: React Router v6 stores a history index in
 * `window.history.state.idx` — it's 0 for the entry the app booted on and
 * >0 once the app has pushed internal navigations. So `idx > 0` means a
 * real previous in-app page exists and `navigate(-1)` is safe.
 */
export function useSmartBack(fallback: string): () => void {
  const navigate = useNavigate()
  return () => {
    const idx = (window.history.state as { idx?: number } | null)?.idx ?? 0
    if (idx > 0) {
      navigate(-1)
    } else {
      navigate(fallback)
    }
  }
}
