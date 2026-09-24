import { Fragment, type ReactNode } from 'react'

/**
 * Renders plain-text comment bodies with **bold** segments and preserved
 * line breaks (paired with `whitespace-pre-wrap` on the container).
 * React escapes everything else — no HTML injection.
 */
export function renderCommentBody(body: string): ReactNode {
  return body.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
    part.startsWith('**') && part.endsWith('**') ? (
      <strong key={i}>{part.slice(2, -2)}</strong>
    ) : (
      <Fragment key={i}>{part}</Fragment>
    )
  )
}
