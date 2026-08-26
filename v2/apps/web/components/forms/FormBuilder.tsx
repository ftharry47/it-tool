'use client'

import * as React from 'react'
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core'
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
  useSortable,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { api } from '@/lib/api'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { GripVertical } from 'lucide-react'

type Field = {
  id: string
  name: string
  key: string
  type: string
  required: boolean
  options: string
  order: number
}

type FormDef = {
  id?: string
  name: string
  issueType: string
  fields: Field[]
}

function SortableField({
  field,
  onChange,
  onRemove,
}: {
  field: Field
  onChange: (field: Field) => void
  onRemove: () => void
}) {
  const { attributes, listeners, setNodeRef, transform, transition } = useSortable({ id: field.id })
  const style = { transform: CSS.Transform.toString(transform), transition }

  return (
    <div ref={setNodeRef} style={style} className="mb-2 flex items-start gap-2 rounded-lg border bg-background p-3">
      <button type="button" className="mt-2 text-muted-foreground" {...attributes} {...listeners}>
        <GripVertical className="h-4 w-4" />
      </button>
      <div className="grid flex-1 gap-2 sm:grid-cols-5">
        <Input
          placeholder="Label"
          value={field.name}
          onChange={(e) => onChange({ ...field, name: e.target.value })}
        />
        <Input
          placeholder="Key"
          value={field.key}
          onChange={(e) => onChange({ ...field, key: e.target.value })}
        />
        <Select
          value={field.type}
          onChange={(e) => onChange({ ...field, type: e.target.value })}
        >
          <option value="TEXT">Text</option>
          <option value="NUMBER">Number</option>
          <option value="SELECT">Select</option>
          <option value="BOOLEAN">Boolean</option>
          <option value="DATE">Date</option>
        </Select>
        <Input
          placeholder={field.type === 'SELECT' ? 'Options (comma separated)' : 'Options'}
          disabled={field.type !== 'SELECT'}
          value={field.options}
          onChange={(e) => onChange({ ...field, options: e.target.value })}
        />
        <div className="flex items-center gap-2">
          <Switch
            checked={field.required}
            onChange={(e: any) => onChange({ ...field, required: e.target.checked })}
          />
          <span className="text-xs">Required</span>
          <Button type="button" variant="outline" size="sm" onClick={onRemove}>
            ×
          </Button>
        </div>
      </div>
    </div>
  )
}

export function FormBuilder() {
  const [defs, setDefs] = React.useState<any[]>([])
  const [form, setForm] = React.useState<FormDef>({ name: '', issueType: 'INCIDENT', fields: [] })

  React.useEffect(() => {
    api('/forms').then(setDefs).catch(() => {})
  }, [])

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  )

  const addField = () => {
    setForm({
      ...form,
      fields: [
        ...form.fields,
        { id: `new-${Date.now()}`, name: '', key: '', type: 'TEXT', required: false, options: '', order: form.fields.length },
      ],
    })
  }

  const updateField = (index: number, value: Field) => {
    const next = [...form.fields]
    next[index] = value
    setForm({ ...form, fields: next })
  }

  const removeField = (index: number) => {
    const next = form.fields.filter((_, i) => i !== index)
    setForm({ ...form, fields: next })
  }

  const handleDragEnd = (event: any) => {
    const { active, over } = event
    if (active.id !== over.id) {
      const oldIndex = form.fields.findIndex((f) => f.id === active.id)
      const newIndex = form.fields.findIndex((f) => f.id === over.id)
      setForm({ ...form, fields: arrayMove(form.fields, oldIndex, newIndex) })
    }
  }

  const save = async () => {
    const payload = {
      ...form,
      fields: form.fields.map((f, i) => ({
        ...f,
        order: i,
        options: f.type === 'SELECT' && f.options ? f.options.split(',').map((s) => s.trim()) : null,
      })),
    }
    await api('/forms', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    setForm({ name: '', issueType: 'INCIDENT', fields: [] })
    api('/forms').then(setDefs)
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Existing Form Definitions</CardTitle>
        </CardHeader>
        <CardContent>
          {defs.map((d) => (
            <p key={d.id} className="text-sm">
              {d.name} ({d.issueType}) — {d.fields.length} fields
            </p>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Create Form Definition</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Input
              placeholder="Form name"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />
            <Select value={form.issueType} onChange={(e) => setForm({ ...form, issueType: e.target.value })}>
              <option value="INCIDENT">Incident</option>
              <option value="REQUEST">Request</option>
            </Select>
          </div>

          <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
            <SortableContext items={form.fields.map((f) => f.id)} strategy={verticalListSortingStrategy}>
              {form.fields.map((field, i) => (
                <SortableField
                  key={field.id}
                  field={field}
                  onChange={(f) => updateField(i, f)}
                  onRemove={() => removeField(i)}
                />
              ))}
            </SortableContext>
          </DndContext>

          <div className="flex gap-2">
            <Button type="button" variant="outline" onClick={addField}>
              + Add Field
            </Button>
            <Button onClick={save} disabled={!form.name || form.fields.length === 0}>
              Save Form
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
