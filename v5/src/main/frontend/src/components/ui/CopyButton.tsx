import { useState } from 'react'
import { Copy, Check } from 'lucide-react'

interface CopyButtonProps {
  html: string
  plain: string
  className?: string
}

export function CopyButton({ html, plain, className }: CopyButtonProps) {
  const [copied, setCopied] = useState(false)

  const handleClick = async () => {
    try {
      const nav = navigator as any
      if (nav.clipboard && 'write' in nav.clipboard) {
        const htmlBlob = new Blob([html], { type: 'text/html' })
        const plainBlob = new Blob([plain], { type: 'text/plain' })
        await nav.clipboard.write([
          new (window as any).ClipboardItem({ 'text/html': htmlBlob, 'text/plain': plainBlob })
        ])
      } else if (nav.clipboard && 'writeText' in nav.clipboard) {
        await nav.clipboard.writeText(plain)
      } else {
        throw new Error('Clipboard API unavailable')
      }
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // silently ignore failed copies
    }
  }

  return (
    <button
      onClick={handleClick}
      title="Copy formatted details"
      className={`inline-flex items-center gap-1 rounded-md border border-border bg-background px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${className ?? ''}`}
    >
      {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
      <span className="hidden sm:inline">{copied ? 'Copied' : 'Copy'}</span>
    </button>
  )
}
