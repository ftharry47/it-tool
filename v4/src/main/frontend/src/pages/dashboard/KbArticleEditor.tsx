import { useEffect, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import ReactMarkdown from 'react-markdown'
import { ArrowLeft, Eye } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { EntityForm } from '../../components/ui/EntityForm'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { Loading } from '../../components/ui/Loading'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { KbVersionHistory } from './KbVersionHistory'

export interface KbVersion {
  id: string
  version: number
  title: string
  category: string
  body: string
  createdAt: string
}

interface KbArticle {
  id: string
  number: number
  title: string
  category: string
  body: string
  status: string
  version: number
  authorName: string
}

const statusTransitions: Record<string, string[]> = {
  DRAFT: ['PENDING_REVIEW'],
  PENDING_REVIEW: ['PUBLISHED', 'DRAFT'],
  PUBLISHED: ['ARCHIVED'],
  ARCHIVED: [],
}

export function KbArticleEditor() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const isNew = id === 'new'

  const [form, setForm] = useState({ title: '', category: '', body: '' })
  const [selectedStatus, setSelectedStatus] = useState('')
  const [preview, setPreview] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  const articleQuery = useQuery<KbArticle>({
    queryKey: ['kb-article', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/kb/${id}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      setForm({ title: data.title ?? '', category: data.category ?? '', body: data.body ?? '' })
      return data
    },
    enabled: !isNew && !!id,
  })

  const versionsQuery = useQuery<KbVersion[]>({
    queryKey: ['kb-versions', id],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, `/api/v1/kb/${id}/versions`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !isNew && !!id,
  })

  const saveMutation = useMutation<KbArticle, Error, { title: string; category: string; body: string; status?: string }>({
    mutationFn: async (payload) => {
      const url = isNew ? '/api/v1/kb' : `/api/v1/kb/${id}`
      const method = isNew ? 'POST' : 'PATCH'
      const res = await fetchWithToken(instance, account!, url, { method, body: JSON.stringify(payload) })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      setSaveError(null)
      setSelectedStatus('')
      queryClient.invalidateQueries({ queryKey: ['kb-articles'] })
      queryClient.invalidateQueries({ queryKey: ['kb-article', id] })
      queryClient.invalidateQueries({ queryKey: ['kb-versions', id] })
      navigate('/dashboard/kb')
    },
    onError: (error) => setSaveError(error.message),
  })

  useEffect(() => {
    if (!isNew && articleQuery.data) {
      setForm({
        title: articleQuery.data.title,
        category: articleQuery.data.category ?? '',
        body: articleQuery.data.body,
      })
    }
  }, [articleQuery.data, isNew])

  if (!isNew && (articleQuery.isLoading || !articleQuery.data)) return <Loading />
  if (!isNew && articleQuery.error) return <ErrorFallback error={articleQuery.error} message="Could not load article." onRetry={() => articleQuery.refetch()} />

  const status = articleQuery.data?.status ?? 'DRAFT'
  const legalTransitions = statusTransitions[status] ?? []
  const editingPublished = status === 'PUBLISHED' && (form.title !== articleQuery.data?.title || form.category !== (articleQuery.data?.category ?? '') || form.body !== articleQuery.data?.body)

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-5xl space-y-6">
        <div className="flex items-center gap-4">
          <Link to="/dashboard/kb" className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
            <ArrowLeft className="h-4 w-4" />
            Back
          </Link>
          <h1 className="text-2xl font-semibold tracking-tight">
            {isNew ? 'New Article' : `Edit #${articleQuery.data?.number}`}
          </h1>
        </div>

        {!isNew && articleQuery.data && (
          <div className="flex items-center gap-3 text-sm text-muted-foreground">
            <StatusBadge status={status} />
            <span>Version {articleQuery.data.version}</span>
            <span>·</span>
            <span>{articleQuery.data.authorName}</span>
          </div>
        )}

        <EntityForm
          fields={[
            { name: 'title', label: 'Title', type: 'text', required: true },
            { name: 'category', label: 'Category', type: 'text' },
            { name: 'body', label: 'Body (Markdown)', type: 'textarea' },
          ]}
          values={form}
          onChange={(name, value) => setForm({ ...form, [name]: value })}
          onSubmit={(e) => {
            e.preventDefault()
            const payload: { title: string; category: string; body: string; status?: string } = form
            if (selectedStatus) payload.status = selectedStatus
            saveMutation.mutate(payload)
          }}
          submitLabel="Save Article"
          pending={saveMutation.isPending}
        />

        {editingPublished && (
          <div className="rounded-md border border-yellow-500/50 bg-yellow-500/10 p-4 text-sm text-yellow-900">
            Editing a published article will snapshot the current version before saving.
          </div>
        )}

        <div className="flex flex-wrap items-end gap-4">
          <div className="space-y-2">
            <label htmlFor="kb-status" className="text-sm font-medium">Change Status</label>
            <select
              id="kb-status"
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value)}
              className="w-48 rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            >
              <option value="">Keep current</option>
              {legalTransitions.map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </div>
          <button
            onClick={() => setPreview(!preview)}
            className="inline-flex items-center gap-2 rounded-md border border-border px-4 py-2 text-sm font-medium transition hover:bg-muted"
          >
            <Eye className="h-4 w-4" />
            {preview ? 'Hide Preview' : 'Show Preview'}
          </button>
        </div>

        {saveError && <p className="text-sm text-destructive">{saveError}</p>}

        {preview && (
          <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
            <h2 className="mb-4 text-lg font-semibold">Preview</h2>
            <article className="prose prose-slate max-w-none">
              <ReactMarkdown>{form.body}</ReactMarkdown>
            </article>
          </section>
        )}

        {!isNew && (
          <section className="rounded-xl border border-border bg-card p-6 shadow-sm">
            <h2 className="mb-4 text-lg font-semibold">Version History</h2>
            {versionsQuery.isLoading ? <Loading /> : <KbVersionHistory versions={versionsQuery.data ?? []} />}
          </section>
        )}
      </div>
    </div>
  )
}
