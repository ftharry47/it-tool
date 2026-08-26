'use client'

import { CatalogGrid } from '@/components/catalog/CatalogGrid'

export default function CatalogPage() {
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Service Catalog</h1>
      <p className="text-muted-foreground">Browse and request common IT services.</p>
      <CatalogGrid />
    </div>
  )
}
