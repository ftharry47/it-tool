import ReactMarkdown from 'react-markdown'
import type { KbVersion } from './KbArticleEditor'
import { formatDateTime } from '../../lib/date'

interface KbVersionHistoryProps {
  versions: KbVersion[]
}

export function KbVersionHistory({ versions }: KbVersionHistoryProps) {
  if (versions.length === 0) return <p className="text-sm text-muted-foreground">No versions yet.</p>
  return (
    <div className="space-y-4">
      {versions.map((version) => (
        <details key={version.id} className="rounded-md border border-border bg-background p-3">
          <summary className="cursor-pointer text-sm font-medium">
            Version {version.version} · {formatDateTime(version.createdAt)}
          </summary>
          <div className="mt-3 space-y-2 text-sm">
            <p><span className="text-muted-foreground">Title:</span> {version.title}</p>
            {version.category && <p><span className="text-muted-foreground">Category:</span> {version.category}</p>}
            <div className="prose prose-sm max-w-none rounded-md bg-muted p-3">
              <ReactMarkdown>{version.body}</ReactMarkdown>
            </div>
          </div>
        </details>
      ))}
    </div>
  )
}
