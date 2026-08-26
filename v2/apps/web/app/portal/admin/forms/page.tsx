'use client'

import { FormBuilder } from '@/components/forms/FormBuilder'

export default function AdminFormsPage() {
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Admin: Form Builder</h1>
      <p className="text-muted-foreground">Build dynamic forms for incidents and requests.</p>
      <FormBuilder />
    </div>
  )
}
