import { useEffect, useMemo, useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { Link, useLocation } from 'react-router-dom'
import { fetchWithToken } from '../../api/client'
import { FormDrawer } from '../../components/ui/FormDrawer'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { SchemaForm, type SchemaField } from '../../components/ui/SchemaForm'
import { ToastStack, type ToastItem } from '../../components/ui/Toast'
import { isValidPhone, PHONE_ERROR } from '../../lib/phone'

interface CatalogItem {
  id: string
  name: string
  description: string | null
  category: string | null
  formSchema: string
  approvalRequired: boolean
  active: boolean
}

interface Location {
  id: string
  name: string
}

export function CatalogBrowse() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const location = useLocation()
  const incidentPath = location.pathname.startsWith('/home') ? '/home/incidents' : '/dashboard/incidents'

  const [selectedItem, setSelectedItem] = useState<CatalogItem | null>(null)
  const [formValues, setFormValues] = useState<Record<string, string>>({})
  const [locationId, setLocationId] = useState('')
  const [phone, setPhone] = useState('')
  const [serverError, setServerError] = useState<string | null>(null)
  const [schemaError, setSchemaError] = useState<string | null>(null)
  const [toasts, setToasts] = useState<ToastItem[]>([])

  const pushToast = (type: ToastItem['type'], message: string) => {
    setToasts((prev) => [...prev, { id: crypto.randomUUID(), type, message }])
  }
  const dismissToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }

  const catalogQuery = useQuery<CatalogItem[]>({
    queryKey: ['catalog-items'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/catalog-items')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      return data.filter((item: CatalogItem) => item.active !== false)
    },
    enabled: !!account,
  })

  const locationsQuery = useQuery<Location[]>({
    queryKey: ['locations'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/locations')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.json()
    },
    enabled: !!account,
  })

  useEffect(() => {
    if (!selectedItem) {
      setSchemaError(null)
      return
    }
    try {
      const parsed = JSON.parse(selectedItem.formSchema)
      if (!Array.isArray(parsed)) {
        setSchemaError('This catalog item has an invalid form schema (not an array).')
      } else {
        setSchemaError(null)
      }
    } catch (e) {
      setSchemaError('This catalog item has malformed form schema JSON.')
    }
  }, [selectedItem])

  const schema = useMemo<SchemaField[]>(() => {
    if (!selectedItem) return []
    try {
      const parsed = JSON.parse(selectedItem.formSchema)
      return Array.isArray(parsed) ? parsed : []
    } catch (e) {
      return []
    }
  }, [selectedItem])

  const createMutation = useMutation<{ id: string; number: number }, Error, { catalogItemId: string; formData: string; locationId: string | null; phone: string | null }>({
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
    onSuccess: (created) => {
      setSelectedItem(null)
      setFormValues({})
      setLocationId('')
      setServerError(null)
      pushToast('success', `Request #${created.number} submitted successfully`)
    },
    onError: (error) => {
      console.error('Service request submission failed:', error)
      setServerError('Something went wrong submitting your request. Please try again or contact IT support.')
    },
  })

  if (catalogQuery.isLoading) return <Loading />
  if (catalogQuery.error) return <ErrorFallback error={catalogQuery.error} message="Could not load catalog." onRetry={() => catalogQuery.refetch()} />

  return (
    <div className="min-h-full bg-background p-6 text-foreground">
      <ToastStack toasts={toasts} onDismiss={dismissToast} />
      <div className="mx-auto max-w-6xl space-y-6">
        <h1 className="text-2xl font-semibold tracking-tight">Service Catalog</h1>
        <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
          <p className="font-medium text-foreground">Not sure where to start?</p>
          <ul className="mt-1 list-inside list-disc">
            <li>
              Is something broken or not working right?{' '}
              <Link to={incidentPath} className="text-primary underline hover:text-primary/80">Report an Incident</Link>{' '}
              instead.
            </li>
            <li>Need a new device, replacement, or access to something? Use the catalog below.</li>
          </ul>
        </div>
        <p className="text-sm text-muted-foreground">Choose a service below to submit a request for your clinic.</p>

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
                  setLocationId('')
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
        dirty={Object.values(formValues).some((v) => v !== '' && v != null) || locationId !== '' || phone !== ''}
        onClose={() => {
          setSelectedItem(null)
          setFormValues({})
          setLocationId('')
          setPhone('')
          setServerError(null)
          setSchemaError(null)
        }}
      >
        {schemaError ? (
          <ErrorFallback error={new Error(schemaError)} message={schemaError} />
        ) : (
          <>
          <div className="mb-4 space-y-2">
            <label htmlFor="sr-location" className="text-sm font-medium">Location <span className="text-destructive">*</span></label>
            <select
              id="sr-location"
              required
              value={locationId}
              onChange={(e) => setLocationId(e.target.value)}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            >
              <option value="">Select location</option>
              {locationsQuery.data?.map((loc) => (
                <option key={loc.id} value={loc.id}>{loc.name}</option>
              ))}
            </select>
          </div>
          <div className="mb-4 space-y-2">
            <label htmlFor="sr-phone" className="text-sm font-medium">Phone Number</label>
            <input
              id="sr-phone"
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="e.g., +1 555-012-3456"
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            />
          </div>
          <SchemaForm
            schema={schema}
            values={formValues}
            onChange={(name, value) => setFormValues({ ...formValues, [name]: value })}
            onSubmit={(values) => {
              if (!selectedItem) return
              if (!locationId.trim()) {
                setServerError('Location is required for every service request')
                return
              }
              if (!isValidPhone(phone)) {
                setServerError(PHONE_ERROR)
                return
              }
              createMutation.mutate({
                catalogItemId: selectedItem.id,
                formData: JSON.stringify(values),
                locationId,
                phone: phone || null,
              })
            }}
            submitLabel="Submit Request"
            pending={createMutation.isPending}
            serverError={serverError}
          />
          </>
        )}
      </FormDrawer>
    </div>
  )
}
