import { useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Loader2, Pencil, Plus, Tags, Trash2 } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { FormDrawer } from '../../components/ui/FormDrawer'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { ToastStack, type ToastItem } from '../../components/ui/Toast'
import { useSmartBack } from '../../lib/useSmartBack'

interface Category {
  id: string
  name: string
  description: string | null
  displayOrder: number
  status: string
  createdAt: string
  updatedAt: string
}

interface CategoryFormState {
  name: string
  description: string
  displayOrder: string
  status: string
}

const EMPTY_FORM: CategoryFormState = { name: '', description: '', displayOrder: '0', status: 'ACTIVE' }

export function CategoryAdmin() {
  const smartBack = useSmartBack('/admin')
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<Category | null>(null)
  const [form, setForm] = useState<CategoryFormState>(EMPTY_FORM)
  const [formError, setFormError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState<Category | null>(null)
  const [toasts, setToasts] = useState<ToastItem[]>([])

  const pushToast = (type: ToastItem['type'], message: string) => {
    setToasts((prev) => [...prev, { id: crypto.randomUUID(), type, message }])
  }
  const dismissToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }

  const categoriesQuery = useQuery<Category[]>({
    queryKey: ['admin-categories'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/categories')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  const saveMutation = useMutation<Category, Error>({
    mutationFn: async () => {
      const body = JSON.stringify({
        name: form.name,
        description: form.description || null,
        displayOrder: parseInt(form.displayOrder, 10) || 0,
        status: form.status,
      })
      const res = await fetchWithToken(
        instance,
        account!,
        editing ? `/api/v1/categories/${editing.id}` : '/api/v1/categories',
        { method: editing ? 'PATCH' : 'POST', body }
      )
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      // Refresh both the admin list and every category consumer
      // (incident create/edit forms, filters, reports).
      queryClient.invalidateQueries({ queryKey: ['admin-categories'] })
      queryClient.invalidateQueries({ queryKey: ['categories'] })
      setDrawerOpen(false)
      setEditing(null)
      setForm(EMPTY_FORM)
      setFormError(null)
      pushToast('success', editing ? 'Category updated successfully' : 'Category created successfully')
    },
    onError: (error) => {
      console.error('Category save failed:', error)
      setFormError('Could not save the category. Please try again or contact IT support.')
    },
  })

  const deleteMutation = useMutation<void, Error, string>({
    mutationFn: async (id) => {
      const res = await fetchWithToken(instance, account!, `/api/v1/categories/${id}`, {
        method: 'DELETE',
      })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-categories'] })
      queryClient.invalidateQueries({ queryKey: ['categories'] })
      setDeleting(null)
      pushToast('success', 'Category deleted')
    },
    onError: (error) => {
      console.error('Category delete failed:', error)
      setDeleting(null)
      pushToast('error', 'Could not delete — the category may be in use by incidents.')
    },
  })

  const openCreate = () => {
    setEditing(null)
    setForm(EMPTY_FORM)
    setFormError(null)
    setDrawerOpen(true)
  }

  const openEdit = (category: Category) => {
    setEditing(category)
    setForm({
      name: category.name,
      description: category.description ?? '',
      displayOrder: String(category.displayOrder),
      status: category.status,
    })
    setFormError(null)
    setDrawerOpen(true)
  }

  if (categoriesQuery.isLoading) return <Loading />
  if (categoriesQuery.error) {
    return <ErrorFallback error={categoriesQuery.error} message="Could not load categories." onRetry={() => categoriesQuery.refetch()} />
  }

  const categories = categoriesQuery.data ?? []

  return (
    <div className="p-6">
      <ToastStack toasts={toasts} onDismiss={dismissToast} />

      <div className="mb-6 flex items-center justify-between">
        <div>
          <div className="flex items-center gap-3">
            <button
                        onClick={smartBack}
                        className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      >
                        <ArrowLeft className="h-4 w-4" />
                        Back
                      </button>
            <div>
              <h1 className="flex items-center gap-2 text-2xl font-semibold">
                          <Tags className="h-6 w-6" />
                          Incident Categories
                        </h1>
              <p className="mt-1 text-sm text-muted-foreground">
                          Manage the categories available on incident forms, filters, and reports.
                        </p>
            </div>
          </div>
        </div>
        <button
          onClick={openCreate}
          className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <Plus className="h-4 w-4" />
          New Category
        </button>
      </div>

      <div className="overflow-hidden rounded-xl border border-border bg-card shadow-sm">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border text-left text-muted-foreground">
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">Description</th>
              <th className="px-4 py-3 font-medium">Order</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {categories.map((category) => (
              <tr key={category.id} className="border-b border-border last:border-0">
                <td className="px-4 py-3 font-medium">{category.name}</td>
                <td className="px-4 py-3 text-muted-foreground">{category.description ?? '—'}</td>
                <td className="px-4 py-3">{category.displayOrder}</td>
                <td className="px-4 py-3">
                  <span
                    className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${
                      category.status === 'ACTIVE'
                        ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300'
                        : 'bg-muted text-muted-foreground'
                    }`}
                  >
                    {category.status}
                  </span>
                </td>
                <td className="px-4 py-3 text-right">
                  <button
                    onClick={() => openEdit(category)}
                    className="mr-2 inline-flex items-center gap-1 rounded-md px-2 py-1 text-sm text-muted-foreground transition hover:bg-muted hover:text-foreground"
                    aria-label={`Edit ${category.name}`}
                  >
                    <Pencil className="h-4 w-4" />
                  </button>
                  <button
                    onClick={() => setDeleting(category)}
                    className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-sm text-destructive transition hover:bg-destructive/10"
                    aria-label={`Delete ${category.name}`}
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </td>
              </tr>
            ))}
            {categories.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-muted-foreground">
                  No categories yet — create the first one.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <FormDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        title={editing ? 'Edit Category' : 'New Category'}
        dirty={JSON.stringify(form) !== JSON.stringify(editing ? {
          name: editing.name,
          description: editing.description ?? '',
          displayOrder: String(editing.displayOrder),
          status: editing.status,
        } : EMPTY_FORM)}
      >
        <form
          onSubmit={(e) => {
            e.preventDefault()
            saveMutation.mutate()
          }}
          className="space-y-4"
        >
          <div className="space-y-2">
            <label htmlFor="category-name" className="text-sm font-medium">
              Name <span className="text-destructive">*</span>
            </label>
            <input
              id="category-name"
              required
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="category-description" className="text-sm font-medium">Description</label>
            <textarea
              id="category-description"
              rows={3}
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <label htmlFor="category-order" className="text-sm font-medium">Display Order</label>
              <input
                id="category-order"
                type="number"
                min={0}
                value={form.displayOrder}
                onChange={(e) => setForm({ ...form, displayOrder: e.target.value })}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              />
            </div>
            <div className="space-y-2">
              <label htmlFor="category-status" className="text-sm font-medium">Status</label>
              <select
                id="category-status"
                value={form.status}
                onChange={(e) => setForm({ ...form, status: e.target.value })}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
              >
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
              </select>
            </div>
          </div>

          {formError && <p className="text-sm text-destructive">{formError}</p>}

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={() => setDrawerOpen(false)}
              className="rounded-md px-4 py-2 text-sm font-medium text-muted-foreground transition hover:bg-muted"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={saveMutation.isPending || !form.name.trim()}
              className="inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              {saveMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {editing ? 'Save Changes' : 'Create Category'}
            </button>
          </div>
        </form>
      </FormDrawer>

      <ConfirmDialog
        open={!!deleting}
        title={`Delete category "${deleting?.name}"?`}
        description="This cannot be undone. Categories in use by incidents cannot be deleted."
        confirmLabel="Delete"
        destructive
        onConfirm={() => deleting && deleteMutation.mutate(deleting.id)}
        onCancel={() => setDeleting(null)}
      />
    </div>
  )
}
