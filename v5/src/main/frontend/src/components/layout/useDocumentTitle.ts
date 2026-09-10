import { useEffect } from 'react'

const APP_NAME = 'Azentro - AlignedCardio'

/**
 * Sets document.title to "Azentro - AlignedCardio | <page>".
 * Pass null/undefined to reset to "Azentro - AlignedCardio".
 * Detail pages can call this again with a more specific title once entity data loads.
 */
export function useDocumentTitle(page?: string | null) {
  useEffect(() => {
    document.title = page ? `${APP_NAME} | ${page}` : APP_NAME
  }, [page])
}
