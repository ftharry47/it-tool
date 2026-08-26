'use client'

import * as React from 'react'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { api } from '@/lib/api'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Select } from '@/components/ui/select'
import { Button } from '@/components/ui/button'
import { DynamicForm } from '@/components/forms/DynamicForm'

const issueSchema = z.object({
  type: z.enum(['INCIDENT', 'REQUEST']),
  title: z.string().min(2, 'Title is required'),
  description: z.string().min(5, 'Description is required'),
  impact: z.enum(['LOW', 'MEDIUM', 'HIGH']),
  category: z.string().min(1, 'Category is required'),
  customFields: z.record(z.any()).default({}),
})

type IssueValues = z.infer<typeof issueSchema>

const categories = ['Hardware', 'Software', 'Network', 'Access', 'Email', 'Printer', 'Mobile', 'Other']

export function IssueForm({ defaultType, defaultCategory }: { defaultType?: string; defaultCategory?: string }) {
  const {
    register,
    handleSubmit,
    control,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<IssueValues>({
    resolver: zodResolver(issueSchema),
    defaultValues: {
      type: (defaultType as any) || 'INCIDENT',
      impact: 'MEDIUM',
      category: defaultCategory || '',
      customFields: {},
    },
  })

  const type = watch('type')
  const [submitted, setSubmitted] = React.useState<any | null>(null)

  const onSubmit = async (data: IssueValues) => {
    const res = await api('/issues', {
      method: 'POST',
      body: JSON.stringify(data),
    })
    setSubmitted(res)
  }

  return (
    <div className="rounded-xl border bg-card p-6 shadow-sm">
      {submitted ? (
        <div className="text-center">
          <h3 className="text-lg font-semibold">Ticket created</h3>
          <p className="text-muted-foreground">{submitted.ticketId}</p>
          <p className="mt-2">{submitted.title}</p>
        </div>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <label className="text-sm font-medium">Type</label>
            <Select {...register('type')}>
              <option value="INCIDENT">Incident</option>
              <option value="REQUEST">Request</option>
            </Select>
            {errors.type && <p className="text-xs text-destructive">{errors.type.message}</p>}
          </div>

          <div>
            <label className="text-sm font-medium">Title</label>
            <Input {...register('title')} placeholder="Short summary" />
            {errors.title && <p className="text-xs text-destructive">{errors.title.message}</p>}
          </div>

          <div>
            <label className="text-sm font-medium">Description</label>
            <Textarea {...register('description')} rows={4} />
            {errors.description && <p className="text-xs text-destructive">{errors.description.message}</p>}
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="text-sm font-medium">Impact</label>
              <Select {...register('impact')}>
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
              </Select>
            </div>
            <div>
              <label className="text-sm font-medium">Category</label>
              <Select {...register('category')}>
                <option value="">Select category</option>
                {categories.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </Select>
              {errors.category && <p className="text-xs text-destructive">{errors.category.message}</p>}
            </div>
          </div>

          <DynamicForm issueType={type} value={watch('customFields')} onChange={(v) => setValue('customFields', v)} />

          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Submitting...' : 'Submit Ticket'}
          </Button>
        </form>
      )}
    </div>
  )
}
