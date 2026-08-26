'use client'

import * as React from 'react'
import Link from 'next/link'
import { useAuth } from '@/components/auth/AuthProvider'
import { SearchHero } from '@/components/search/SearchHero'
import { CatalogGrid } from '@/components/catalog/CatalogGrid'
import { api } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { PlusCircle, ClipboardList, LayoutGrid } from 'lucide-react'

export default function PortalHomePage() {
  const { user } = useAuth()
  const [metrics, setMetrics] = React.useState({ total: 0, submitted: 0, inProgress: 0, resolved: 0 })

  React.useEffect(() => {
    api('/issues?mine=true')
      .then((issues: any[]) => {
        setMetrics({
          total: issues.length,
          submitted: issues.filter((i) => i.status === 'SUBMITTED').length,
          inProgress: issues.filter((i) => i.status === 'IN_PROGRESS').length,
          resolved: issues.filter((i) => i.status === 'RESOLVED').length,
        })
      })
      .catch(() => {})
  }, [])

  return (
    <div className="space-y-8">
      <div className="rounded-2xl bg-gradient-to-br from-primary/90 to-primary px-6 py-12 text-center text-primary-foreground">
        <h1 className="text-3xl font-bold md:text-4xl">Welcome to Dev-IT, {user?.name}</h1>
        <p className="mx-auto mt-3 max-w-xl text-primary-foreground/90">
          Search knowledge articles, request services, or report an issue — all in one place.
        </p>
        <div className="mx-auto mt-8 max-w-2xl">
          <SearchHero />
        </div>
        <div className="mt-8 flex justify-center gap-3">
          <Link href="/portal/report">
            <Button variant="secondary" className="gap-2">
              <PlusCircle className="h-4 w-4" />
              Report Issue
            </Button>
          </Link>
          <Link href="/portal/requests">
            <Button variant="secondary" className="gap-2">
              <ClipboardList className="h-4 w-4" />
              My Requests
            </Button>
          </Link>
          <Link href="/portal/catalog">
            <Button variant="secondary" className="gap-2">
              <LayoutGrid className="h-4 w-4" />
              Catalog
            </Button>
          </Link>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-4">
        <MetricCard title="My Requests" value={metrics.total} />
        <MetricCard title="Submitted" value={metrics.submitted} />
        <MetricCard title="In Progress" value={metrics.inProgress} />
        <MetricCard title="Resolved" value={metrics.resolved} />
      </div>

      <section>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Service Catalog</h2>
          <Link href="/portal/catalog" className="text-sm text-primary hover:underline">
            View all
          </Link>
        </div>
        <CatalogGrid limit={6} />
      </section>
    </div>
  )
}

function MetricCard({ title, value }: { title: string; value: number }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <p className="text-3xl font-bold">{value}</p>
      </CardContent>
    </Card>
  )
}
