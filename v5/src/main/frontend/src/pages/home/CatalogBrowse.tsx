import { useEffect, useMemo, useState } from 'react'
import { useMsal } from '@azure/msal-react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { fetchWithToken } from '../../api/client'
import { FormDrawer } from '../../components/ui/FormDrawer'
import { Loading } from '../../components/ui/Loading'
import { ErrorFallback } from '../../components/ui/ErrorFallback'
import { SchemaForm, type SchemaField } from '../../components/ui/SchemaForm'
import { SubmissionNarrative, type SubmissionNarrativeStep } from '../../components/ui/SubmissionNarrative'
import { isValidPhone, normalizePhone, PHONE_ERROR } from '../../lib/phone'
import { ArrowLeft } from 'lucide-react'
import { useSmartBack } from '../../lib/useSmartBack'

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

interface PriorityOption {
  id: string
  name: string
}

interface CreatedServiceRequest {
  id: string
  number: number
  status: string
  approvalDecision: string
}

export function CatalogBrowse() {
  const { instance, accounts } = useMsal()
  const account = accounts[0]
  const location = useLocation()
  const navigate = useNavigate()
  const smartBack = useSmartBack(location.pathname.startsWith('/home') ? '/home' : '/dashboard')
  const incidentPath = location.pathname.startsWith('/home') ? '/home/incidents' : '/dashboard/incidents'
  const isHome = location.pathname.startsWith('/home')

  const [selectedItem, setSelectedItem] = useState<CatalogItem | null>(null)
  const [formValues, setFormValues] = useState<Record<string, string>>({})
  const [locationId, setLocationId] = useState('')
  const [priorityId, setPriorityId] = useState('')
  const [phone, setPhone] = useState('')
  const [serverError, setServerError] = useState<string | null>(null)
  const [schemaError, setSchemaError] = useState<string | null>(null)
  const [narrativeOpen, setNarrativeOpen] = useState(false)
  const [narrativeStep, setNarrativeStep] = useState(0)
  const [narrativeSteps, setNarrativeSteps] = useState<SubmissionNarrativeStep[]>([])
  const [narrativeSuccess, setNarrativeSuccess] = useState({ title: '', subtitle: '' })

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

  const prioritiesQuery = useQuery<PriorityOption[]>({
    queryKey: ['priorities'],
    queryFn: async () => {
      const res = await fetchWithToken(instance, account!, '/api/v1/incidents/priorities')
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

  const createMutation = useMutation<CreatedServiceRequest, Error, { catalogItemId: string; formData: string; locationId: string | null; phone: string | null; priorityId: string | null }>({
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
      const needsApproval = created.status === 'PENDING_APPROVAL'
      const steps: SubmissionNarrativeStep[] = [
        { id: 'validate', label: 'Validating request...' },
        { id: 'connect', label: 'Connecting to server...' },
        { id: 'route', label: needsApproval ? 'Routing for approval...' : 'Submitting to IT team...' },
      ]
      if (!needsApproval) {
        steps.push({ id: 'notify', label: 'Notifying the team...' })
      }
      setNarrativeSteps(steps)
      setNarrativeStep(steps.length - (needsApproval ? 1 : 2))
      setTimeout(() => {
        setNarrativeStep(steps.length - 1)
        setTimeout(() => {
          setNarrativeStep(steps.length)
          setNarrativeSuccess({
            title: `Request #${created.number} submitted successfully`,
            subtitle: needsApproval ? 'Awaiting manager approval.' : 'IT team will process your request shortly.',
          })
          setTimeout(() => {
            setNarrativeOpen(false)
            setNarrativeStep(0)
            navigate(isHome ? `/home/service-requests/${created.id}` : `/dashboard/service-requests/${created.id}`)
          }, 1200)
        }, 400)
      }, 400)
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
      <SubmissionNarrative
        open={narrativeOpen}
        steps={narrativeSteps}
        current={narrativeStep}
        successTitle={narrativeSuccess.title}
        successSubtitle={narrativeSuccess.subtitle}
      />
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex items-center gap-3">
          <button
                    onClick={smartBack}
                    className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    <ArrowLeft className="h-4 w-4" />
                    Back
                  </button>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Service Catalog</h1>
          </div>
        </div>
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
                  setPriorityId('')
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
          setPriorityId('')
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
            <label htmlFor="sr-priority" className="text-sm font-medium">Priority</label>
            <select
              id="sr-priority"
              value={priorityId}
              onChange={(e) => setPriorityId(e.target.value)}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring"
            >
              <option value="">Default</option>
              {prioritiesQuery.data?.map((p) => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
          </div>
          <div className="mb-4 space-y-2">
            <label htmlFor="sr-phone" className="text-sm font-medium">Phone Number <span className="text-destructive">*</span></label>
            <input
              id="sr-phone"
              type="tel"
              required
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="e.g., 555-123-4567"
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
              setNarrativeOpen(true)
              setNarrativeStep(0)
              setNarrativeSteps([
                { id: 'validate', label: 'Validating request...' },
                { id: 'connect', label: 'Connecting to server...' },
                { id: 'route', label: 'Submitting request...' },
              ])
              setTimeout(() => {
                setNarrativeStep(1)
                setTimeout(() => {
                  setNarrativeStep(2)
                  createMutation.mutate({
                    catalogItemId: selectedItem.id,
                    formData: JSON.stringify(values),
                    locationId,
                    phone: normalizePhone(phone),
                    priorityId: priorityId || null,
                  })
                }, 400)
              }, 400)
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
