'use client'

import { useParams } from 'next/navigation'
import { ArticleViewer } from '@/components/kb/ArticleViewer'

export default function KbArticlePage() {
  const { id } = useParams() as { id: string }
  return <ArticleViewer id={id} />
}
