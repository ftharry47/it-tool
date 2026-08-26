'use client'

import { useParams } from 'next/navigation'
import { TicketDetail } from '@/components/issues/TicketDetail'

export default function RequestDetailPage() {
  const { id } = useParams() as { id: string }
  return <TicketDetail id={id} />
}
