'use client'

import * as React from 'react'
import { api } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'

const steps = ['SUBMITTED', 'IN_PROGRESS', 'RESOLVED']

export function TicketDetail({ id }: { id: string }) {
  const [issue, setIssue] = React.useState<any>(null)
  const [comments, setComments] = React.useState<any[]>([])
  const [newComment, setNewComment] = React.useState('')
  const [loading, setLoading] = React.useState(false)

  const load = async () => {
    const [i, c] = await Promise.all([api(`/issues/${id}`), api(`/issues/${id}/comments`)])
    setIssue(i)
    setComments(c)
  }

  React.useEffect(() => {
    load().catch(() => {})
  }, [id])

  const postComment = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newComment.trim()) return
    setLoading(true)
    await api(`/issues/${id}/comments`, {
      method: 'POST',
      body: JSON.stringify({ content: newComment }),
    })
    setNewComment('')
    await load()
    setLoading(false)
  }

  if (!issue) return <p className="text-muted-foreground">Loading...</p>

  const stepIndex = steps.indexOf(issue.status) >= 0 ? steps.indexOf(issue.status) : 0

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <div className="flex items-start justify-between">
            <div>
              <CardTitle className="text-xl">{issue.title}</CardTitle>
              <CardDescription className="font-mono text-xs">{issue.ticketId}</CardDescription>
            </div>
            <Badge variant="default">{issue.status}</Badge>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-3">
            <div>
              <p className="text-xs text-muted-foreground">Type</p>
              <p className="font-medium">{issue.type}</p>
            </div>
            <div>
              <p className="text-xs text-muted-foreground">Category</p>
              <p className="font-medium">{issue.category}</p>
            </div>
            <div>
              <p className="text-xs text-muted-foreground">Impact</p>
              <p className="font-medium">{issue.impact}</p>
            </div>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Description</p>
            <p className="mt-1 rounded-md bg-muted p-3 text-sm">{issue.description}</p>
          </div>

          <div>
            <p className="mb-2 text-xs text-muted-foreground">Progress</p>
            <div className="flex items-center justify-between">
              {steps.map((s, i) => (
                <div key={s} className="flex flex-1 items-center">
                  <div
                    className={`flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold ${
                      i <= stepIndex ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground'
                    }`}
                  >
                    {i + 1}
                  </div>
                  <p className="ml-2 text-xs font-medium">{s.replace('_', ' ')}</p>
                  {i < steps.length - 1 && (
                    <div className={`mx-2 h-1 flex-1 ${i < stepIndex ? 'bg-primary' : 'bg-muted'}`} />
                  )}
                </div>
              ))}
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Activity</CardTitle>
          <CardDescription>Comments and updates</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {comments.length === 0 && <p className="text-sm text-muted-foreground">No comments yet.</p>}
          {comments.map((c) => (
            <div key={c.id} className="flex flex-col gap-1 rounded-md bg-muted p-3">
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <span className="font-medium text-foreground">{c.author?.name || 'Unknown'}</span>
                <span>• {new Date(c.createdAt).toLocaleString()}</span>
              </div>
              <p className="text-sm">{c.content}</p>
            </div>
          ))}
          <form onSubmit={postComment} className="flex gap-2">
            <Textarea
              value={newComment}
              onChange={(e) => setNewComment(e.target.value)}
              placeholder="Add a comment..."
              rows={2}
              className="min-h-0 flex-1"
            />
            <Button type="submit" disabled={loading}>
              Post
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
