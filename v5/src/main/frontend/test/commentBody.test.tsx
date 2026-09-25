/**
 * XSS regression for renderCommentBody: the **bold** → <strong>
 * substitution must be the ONLY markup that survives — React escaping
 * applies to everything else in the comment body.
 *
 * Run: npm run test:comments
 */
import { renderToStaticMarkup } from 'react-dom/server'
import { createElement } from 'react'
import assert from 'node:assert'
import { renderCommentBody } from '../src/lib/commentBody'

const html = (s: string) =>
  renderToStaticMarkup(createElement('p', null, renderCommentBody(s)))

// Raw HTML must render as literal text
assert.strictEqual(html('<script>alert(1)</script>'),
  '<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>')
assert.strictEqual(html('<img src=x onerror=alert(1)>'),
  '<p>&lt;img src=x onerror=alert(1)&gt;</p>')
assert.strictEqual(html('<a href="javascript:alert(1)">x</a>'),
  '<p>&lt;a href=&quot;javascript:alert(1)&quot;&gt;x&lt;/a&gt;</p>')

// Bold converts; hostile markup around it stays escaped
assert.strictEqual(html('normal **bold** <script>alert(1)</script> tail'),
  '<p>normal <strong>bold</strong> &lt;script&gt;alert(1)&lt;/script&gt; tail</p>')

// Multi-line + bold coexist
assert.strictEqual(html('line one\n**second** line'),
  '<p>line one\n<strong>second</strong> line</p>')

console.log('commentBody XSS tests: 5/5 passed')
