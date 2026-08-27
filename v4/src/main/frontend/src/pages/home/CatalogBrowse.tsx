import { useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { fetchWithToken } from '../../api/client'
import { FormDrawer } from '../../components/ui/FormDrawer'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { SchemaForm, type SchemaField } from '../../components/ui/SchemaForm'

interface CatalogItem {
  id: string
  name: string
  description: string | null
  category: string | null
  formSchema: string
  approvalRequired: boolean
  active: boolean
}

export function CatalogBrowse() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]

  const [selectedItem, setSelectedItem] = useState<CatalogItem | null>(null)
  const [formValues, setFormValues] = useState<Record<string, string>>({})
  const [serverError, setServerError] = useState<string | null>(null)
  const [schemaError, setSchemaError] = useState<string | null>(null)

  const catalogQuery = useQuery<CatalogItem[]>({
    queryKey: ['catalog-items'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/catalog-items')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      return data.filter((item: CatalogItem) => item.active !== false)
    },
  })

  const schema: SchemaField[] = (() => {
    if (!selectedItem) return []
    try {
      const parsed = JSON.parse(selectedItem.formSchema)
      if (!Array.isArray(parsed)) {
        setSchemaError('This catalog item has an invalid form schema (not an array).')
        return []
      }
      setSchemaError(null)
      return parsed
    } catch (e) {
      setSchemaError('This catalog item has malformed form schema JSON.')
      return []
    }
  })()

  const createMutation = useMutation<CatalogItem, Error, { catalogItemId: string; formData: string }>({
    mutationFn: async (payload) => {
      const res = await fetchWithToken(instance, account!, '/api/v1/service-requests', {
        method: 'POST',
        body: JSON.stringify(payload),
      })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      return res.json()
    },
    onSuccess: () => {
      setSelectedItem(null)
      setFormValues({})
      setServerError(null)
      alert('Request submitted successfully.')
    },
    onError: (error) => {
      setServerError(error.message)
    },
  })

  if (catalogQuery.isLoading) return <Loading />
  if (catalogQuery.error) return <ErrorFallback error={catalogQuery.error} message="Could not load catalog." onRetry={() => catalogQuery.refetch()} />

  return (
    <div className="min-h-screen bg-background p-6 text-foreground">
      <div className="mx-auto max-w-6xl space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">Service Catalog</h1>
        <p className="text-sm text-muted-foreground">Choose a service below to submit a request.</p>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {catalogQuery.data?.map((item) => (
            <div
              key={item.id}
              className="rounded-xl border border-border bg-card p-5 shadow-sm transition hover:bg-muted/50"
            >
              <h2 className="text-lg font-semibold">{item.name}</h2>
              {item.category && <p className="text-xs text-muted-foreground">{item.category}</p>}
              <p className="mt-2 text-sm text-muted-foreground">{item.description ?? 'No description.'}</p>
              <button
                onClick={() => {
                  setSelectedItem(item)
                  setFormValues({})
                  setServerError(null)
                }}
                className="mt-4 inline-flex w-full items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                Request
              </button>
            </div>
          ))}
        </div>

        {catalogQuery.data?.length === 0 && (
          <p className="text-center text-muted-foreground">No catalog items available.</p>
        )}
      </div>

      <FormDrawer
        open={!!selectedItem}
        title={selectedItem?.name ?? 'Request'}
        onClose={() => {
          setSelectedItem(null)
          setFormValues({})
          setServerError(null)
          setSchemaError(null)
        }}
      >
        {schemaError ? (
          <ErrorFallback error={new Error(schemaError)} message={schemaError} />
        ) : (
          <SchemaForm
            schema={schema}
            values={formValues}
            onChange={(name, value) => setFormValues({ ...formValues, [name]: value })}
            onSubmit={(values) => {
              if (!selectedItem) return
              createMutation.mutate({
                catalogItemId: selectedItem.id,
                formData: JSON.stringify(values),
              })
            }}
            submitLabel="Submit Request"
            pending={createMutation.isPending}
            serverError={serverError}
          />
        )}
      </FormDrawer>
    </div>
  )
}
