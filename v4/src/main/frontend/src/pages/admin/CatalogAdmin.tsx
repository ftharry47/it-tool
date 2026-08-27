import { useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { fetchWithToken } from '../../api/client'
import { DataTable } from '../../components/ui/DataTable'
import { EntityForm } from '../../components/ui/EntityForm'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { FormDrawer } from '../../components/ui/FormDrawer'
import { Loading } from '../../components/ui/Loading'
import { SchemaForm, isValidSchema } from '../../components/ui/SchemaForm'

interface CatalogItem {
  id: string
  name: string
  description: string | null
  category: string | null
  formSchema: string
  fulfillmentTasks: string
  approvalRequired: boolean
  approverId: string | null
  active: boolean
}

interface CatalogFormValues {
  [key: string]: string
  name: string
  description: string
  category: string
  formSchema: string
  fulfillmentTasks: string
  approvalRequired: string
  approverId: string
  active: string
}

const initialValues: CatalogFormValues = {
  name: '',
  description: '',
  category: '',
  formSchema: '[]',
  fulfillmentTasks: '[]',
  approvalRequired: 'false',
  approverId: '',
  active: 'true',
}

function valuesFromItem(item: CatalogItem | null): CatalogFormValues {
  if (!item) return initialValues
  return {
    name: item.name ?? '',
    description: item.description ?? '',
    category: item.category ?? '',
    formSchema: item.formSchema ?? '[]',
    fulfillmentTasks: item.fulfillmentTasks ?? '[]',
    approvalRequired: item.approvalRequired ? 'true' : 'false',
    approverId: item.approverId ?? '',
    active: item.active ? 'true' : 'false',
  }
}

export function CatalogAdmin() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const queryClient = useQueryClient()

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [selectedItem, setSelectedItem] = useState<CatalogItem | null>(null)
  const [values, setValues] = useState<CatalogFormValues>(initialValues)
  const [parseError, setParseError] = useState<string | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)

  const catalogQuery = useQuery<CatalogItem[]>({
    queryKey: ['catalog-items'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/catalog-items')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
  })

  const parsedSchema = (() => {
    try {
      const parsed = JSON.parse(values.formSchema)
      if (!isValidSchema(parsed)) {
        setParseError('form_schema must be an array of fields with at least a "name" and a valid "type".')
        return []
      }
      setParseError(null)
      return parsed
    } catch (e) {
      setParseError('form_schema is not valid JSON.')
      return []
    }
  })()

  const parsedFulfillment = (() => {
    try {
      const parsed = JSON.parse(values.fulfillmentTasks)
      if (!Array.isArray(parsed)) {
        return null
      }
      return parsed
    } catch {
      return null
    }
  })()

  const saveMutation = useMutation<CatalogItem, Error, CatalogFormValues>({
    mutationFn: async (formValues) => {
      const payload = {
        name: formValues.name,
        description: formValues.description,
        category: formValues.category,
        formSchema: formValues.formSchema,
        fulfillmentTasks: formValues.fulfillmentTasks,
        approvalRequired: formValues.approvalRequired === 'true',
        approverId: formValues.approverId || null,
        active: formValues.active === 'true',
      }
      const url = selectedItem ? `/api/v1/catalog-items/${selectedItem.id}` : '/api/v1/catalog-items'
      const method = selectedItem ? 'PATCH' : 'POST'
      const res = await fetchWithToken(instance, account!, url, { method, body: JSON.stringify(payload) })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      setDrawerOpen(false)
      setSelectedItem(null)
      setValues(initialValues)
      setServerError(null)
      queryClient.invalidateQueries({ queryKey: ['catalog-items'] })
    },
    onError: (error) => setServerError(error.message),
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setServerError(null)
    if (parseError) return
    saveMutation.mutate(values)
  }

  const openEdit = (item: CatalogItem) => {
    setSelectedItem(item)
    setValues(valuesFromItem(item))
    setParseError(null)
    setServerError(null)
    setDrawerOpen(true)
  }

  const openCreate = () => {
    setSelectedItem(null)
    setValues(initialValues)
    setParseError(null)
    setServerError(null)
    setDrawerOpen(true)
  }

  if (catalogQuery.isLoading) return <Loading />
  if (catalogQuery.error) return <ErrorFallback error={catalogQuery.error} message="Could not load catalog items." onRetry={() => catalogQuery.refetch()} />

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold tracking-tight">Catalog Administration</h1>
          <button
            onClick={openCreate}
            className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <Plus className="h-4 w-4" />
            New Catalog Item
          </button>
        </div>

        <DataTable<CatalogItem>
          caption="Catalog items"
          columns={[
            { key: 'name', header: 'Name' },
            { key: 'category', header: 'Category' },
            { key: 'active', header: 'Active', render: (row) => (row.active ? 'Yes' : 'No') },
            { key: 'approvalRequired', header: 'Approval', render: (row) => (row.approvalRequired ? 'Yes' : 'No') },
            {
              key: 'actions',
              header: 'Actions',
              render: (row) => (
                <button
                  onClick={() => openEdit(row)}
                  className="rounded-md border border-border px-3 py-1 text-sm hover:bg-muted"
                >
                  Edit
                </button>
              ),
            },
          ]}
          data={catalogQuery.data ?? []}
          getRowKey={(row) => row.id}
          emptyText="No catalog items."
        />
      </div>

      <FormDrawer
        open={drawerOpen}
        title={selectedItem ? 'Edit Catalog Item' : 'New Catalog Item'}
        onClose={() => { setDrawerOpen(false); setSelectedItem(null); setValues(initialValues); setParseError(null); setServerError(null) }}
      >
        <EntityForm
          fields={[
            { name: 'name', label: 'Name', type: 'text', required: true },
            { name: 'description', label: 'Description', type: 'textarea' },
            { name: 'category', label: 'Category', type: 'text' },
            { name: 'formSchema', label: 'Form Schema JSON', type: 'textarea' },
            { name: 'fulfillmentTasks', label: 'Fulfillment Tasks JSON', type: 'textarea' },
            { name: 'approverId', label: 'Approver ID (UUID)', type: 'text' },
            { name: 'approvalRequired', label: 'Approval Required', type: 'boolean' },
            { name: 'active', label: 'Active', type: 'boolean' },
          ]}
          values={values}
          onChange={(name, value) => {
            setValues({ ...values, [name]: value })
            if (name === 'formSchema') setParseError(null)
          }}
          onSubmit={handleSubmit}
          submitLabel={selectedItem ? 'Save' : 'Create'}
          pending={saveMutation.isPending}
        />

        <div className="mt-6 rounded-xl border border-border bg-card p-4">
          <h3 className="mb-2 text-sm font-semibold">Form Preview</h3>
          {parseError ? (
            <p className="text-sm text-destructive">{parseError}</p>
          ) : (
            <SchemaForm
              schema={parsedSchema}
              values={{}}
              onChange={() => {}}
              onSubmit={() => {}}
              submitLabel="Preview"
            />
          )}
          {parsedFulfillment && parsedFulfillment.length > 0 && (
            <div className="mt-4">
              <h4 className="mb-1 text-sm font-medium text-muted-foreground">Fulfillment Tasks Preview</h4>
              <ul className="list-inside list-disc text-sm text-muted-foreground">
                {parsedFulfillment.map((task: { description?: string }, i: number) => (
                  <li key={i}>{task.description ?? `Task ${i + 1}`}</li>
                ))}
              </ul>
            </div>
          )}
        </div>

        {serverError && <p className="mt-4 text-sm text-destructive">{serverError}</p>}
      </FormDrawer>
    </div>
  )
}
