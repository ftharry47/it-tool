import { useRef, useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { useQuery } from '@tanstack/react-query'
import { Bold } from 'lucide-react'
import { fetchWithToken } from '../../api/client'

interface MentionUser {
  id: string
  displayName: string
  email: string
}

interface MentionTextareaProps {
  value: string
  onChange: (v: string) => void
  placeholder?: string
  rows?: number
  className?: string
}

/**
 * Composer textarea with a Bold button (wraps the selection in **…**,
 * rendered as <strong> in the thread and emails) and an @mention picker —
 * typing @ opens a filtered list of real org users; picking one inserts
 * "@Display Name " which the backend resolves to a MENTION notification.
 */
export function MentionTextarea({ value, onChange, placeholder, rows = 4, className }: MentionTextareaProps) {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const ref = useRef<HTMLTextAreaElement>(null)
  const [mentionStart, setMentionStart] = useState<number | null>(null)
  const [mentionQuery, setMentionQuery] = useState('')

  const usersQuery = useQuery<MentionUser[]>({
    queryKey: ['users'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/users')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
    staleTime: 60_000,
  })

  const detectMention = (next: string, caret: number) => {
    const upto = next.slice(0, caret)
    const m = /(?:^|[\s(])@([\w. ]*)$/.exec(upto)
    if (m) {
      setMentionStart(caret - m[0].length + (m[0].startsWith('@') ? 0 : 1))
      setMentionQuery(m[1])
    } else {
      setMentionStart(null)
    }
  }

  const matches = mentionStart === null
    ? []
    : (usersQuery.data ?? [])
        .filter((u) =>
          u.displayName.toLowerCase().includes(mentionQuery.toLowerCase())
          || u.email.toLowerCase().includes(mentionQuery.toLowerCase()))
        .slice(0, 6)

  const insertMention = (u: MentionUser) => {
    const ta = ref.current
    if (!ta || mentionStart === null) return
    const caret = ta.selectionStart
    const next = `${value.slice(0, mentionStart)}@${u.displayName} ${value.slice(caret)}`
    onChange(next)
    setMentionStart(null)
    requestAnimationFrame(() => {
      ta.focus()
      const pos = mentionStart + u.displayName.length + 2
      ta.setSelectionRange(pos, pos)
    })
  }

  const applyBold = () => {
    const ta = ref.current
    if (!ta) return
    const { selectionStart: s, selectionEnd: e } = ta
    const selected = value.slice(s, e)
    const next = `${value.slice(0, s)}**${selected || 'bold text'}**${value.slice(e)}`
    onChange(next)
    requestAnimationFrame(() => {
      ta.focus()
      if (selected) {
        ta.setSelectionRange(s + 2, e + 2)
      } else {
        ta.setSelectionRange(s + 2, s + 11)
      }
    })
  }

  return (
    <div className="relative">
      <div className="mb-1 flex justify-end">
        <button
          type="button"
          onClick={applyBold}
          title="Bold (wraps selection in **)"
          className="rounded-md border border-border p-1.5 text-muted-foreground transition hover:bg-muted hover:text-foreground"
        >
          <Bold className="h-3.5 w-3.5" />
        </button>
      </div>
      <textarea
        ref={ref}
        value={value}
        rows={rows}
        placeholder={placeholder}
        onChange={(e) => {
          onChange(e.target.value)
          detectMention(e.target.value, e.target.selectionStart)
        }}
        onKeyDown={(e) => {
          if (e.key === 'Escape') setMentionStart(null)
        }}
        onBlur={() => setTimeout(() => setMentionStart(null), 150)}
        className={className}
      />
      {matches.length > 0 && (
        <ul className="absolute z-10 mt-1 max-h-48 w-64 overflow-auto rounded-md border border-border bg-popover shadow-lg">
          {matches.map((u) => (
            <li key={u.id}>
              <button
                type="button"
                onMouseDown={(e) => {
                  e.preventDefault()
                  insertMention(u)
                }}
                className="w-full px-3 py-2 text-left text-sm transition hover:bg-muted"
              >
                <span className="font-medium text-foreground">{u.displayName}</span>
                <span className="ml-2 text-xs text-muted-foreground">{u.email}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
