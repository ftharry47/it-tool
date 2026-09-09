import { useEffect, useRef } from 'react'

const EVENTS = ['mousemove', 'keydown', 'click', 'scroll', 'touchstart']
const DEFAULT_TIMEOUT_MS = 15 * 60 * 1000

export function useIdleTimeout({
  timeoutMs = DEFAULT_TIMEOUT_MS,
  onIdle,
  disabled = false,
}: {
  timeoutMs?: number
  onIdle: () => void
  disabled?: boolean
}) {
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const lastResetRef = useRef(0)

  useEffect(() => {
    if (disabled) return

    const reset = () => {
      const now = Date.now()
      // Throttle resets to once per second so rapid UI events don't churn.
      if (now - lastResetRef.current < 1000) return
      lastResetRef.current = now
      if (timeoutRef.current) clearTimeout(timeoutRef.current)
      timeoutRef.current = setTimeout(onIdle, timeoutMs)
    }

    reset()

    EVENTS.forEach((event) => window.addEventListener(event, reset, { passive: true }))

    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current)
      EVENTS.forEach((event) => window.removeEventListener(event, reset))
    }
  }, [timeoutMs, onIdle, disabled])
}
