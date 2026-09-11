import { CheckCircle2, Loader2 } from 'lucide-react'

export interface SubmissionNarrativeStep {
  id: string
  label: string
}

interface SubmissionNarrativeProps {
  open: boolean
  steps: SubmissionNarrativeStep[]
  current: number
  successTitle: string
  successSubtitle: string
}

export function SubmissionNarrative({ open, steps, current, successTitle, successSubtitle }: SubmissionNarrativeProps) {
  if (!open) return null

  const success = current >= steps.length

  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center bg-background/95 p-4 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-xl border border-border bg-card p-8 shadow-lg transition-all duration-500">
        {!success ? (
          <div className="space-y-5">
            <h2 className="text-center text-lg font-semibold tracking-tight">Submitting request</h2>
            <ul className="space-y-3">
              {steps.map((step, i) => {
                const completed = i < current
                const active = i === current
                return (
                  <li
                    key={step.id}
                    className={`flex items-center gap-3 text-sm transition-all duration-500 ${
                      completed || active ? 'text-foreground' : 'text-muted-foreground'
                    }`}
                  >
                    <span className="flex h-6 w-6 shrink-0 items-center justify-center">
                      {completed ? (
                        <CheckCircle2 className="h-5 w-5 text-primary" />
                      ) : active ? (
                        <Loader2 className="h-5 w-5 animate-spin text-primary" />
                      ) : (
                        <span className="h-2 w-2 rounded-full bg-muted-foreground/40" />
                      )}
                    </span>
                    <span className={active ? 'font-medium' : ''}>{step.label}</span>
                  </li>
                )
              })}
            </ul>
          </div>
        ) : (
          <div className="space-y-4 text-center">
            <div className="flex justify-center">
              <CheckCircle2 className="h-12 w-12 text-primary" />
            </div>
            <h2 className="text-lg font-semibold tracking-tight">{successTitle}</h2>
            <p className="text-sm text-muted-foreground">{successSubtitle}</p>
          </div>
        )}
      </div>
    </div>
  )
}
