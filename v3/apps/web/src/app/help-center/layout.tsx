'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@/lib/api'
import { Shell } from '@/components/shell'

export default function HelpCenterLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const { loaded, user } = useAuth()
  const router = useRouter()

  useEffect(() => {
    if (loaded && !user) {
      const returnTo = window.location.pathname + window.location.search
      router.replace(`/login?returnTo=${encodeURIComponent(returnTo)}`)
    }
  }, [loaded, user, router])

  if (!loaded) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p className="text-sm text-muted-foreground">Loading...</p>
      </div>
    )
  }

  if (!user) {
    return null
  }

  return <Shell>{children}</Shell>
}
