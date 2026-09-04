import { Link } from 'react-router-dom'
import { Ticket, BookOpen, LayoutGrid, ArrowRight } from 'lucide-react'

const cards = [
  {
    to: '/home/incidents',
    icon: Ticket,
    title: 'Report & Track Issues',
    description: 'Create a new incident or check the status of issues you’ve already reported.',
    cta: 'My Incidents',
  },
  {
    to: '/home/catalog',
    icon: LayoutGrid,
    title: 'Request Services & Equipment',
    description: 'Browse the catalog for software, hardware, access, and other service requests.',
    cta: 'Service Catalog',
  },
  {
    to: '/home/kb',
    icon: BookOpen,
    title: 'Find Answers',
    description: 'Search knowledge base articles for self-help, how-to guides, and FAQs.',
    cta: 'Knowledge Base',
  },
]

export function Home() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4 py-12">
      <div className="w-full max-w-4xl space-y-6 text-center">
        <h1 className="text-3xl font-semibold tracking-tight text-card-foreground">Self-Service Portal</h1>
        <p className="mx-auto max-w-xl text-sm text-muted-foreground">
          Get help, track requests, and find answers in one place.
        </p>
      </div>

      <div className="mt-10 grid w-full max-w-4xl gap-6 md:grid-cols-3">
        {cards.map((card) => {
          const Icon = card.icon
          return (
            <Link
              key={card.to}
              to={card.to}
              className="group flex flex-col rounded-xl border border-border bg-card p-6 shadow-sm transition hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-primary">
                <Icon className="h-5 w-5" />
              </div>
              <h2 className="mb-2 text-lg font-semibold text-card-foreground">{card.title}</h2>
              <p className="mb-6 flex-1 text-sm text-muted-foreground">{card.description}</p>
              <span className="inline-flex items-center gap-2 text-sm font-medium text-primary group-hover:underline">
                {card.cta}
                <ArrowRight className="h-4 w-4" />
              </span>
            </Link>
          )
        })}
      </div>
    </div>
  )
}
