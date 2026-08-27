import { cn } from '@/lib/utils'

export function AxiomDeskSymbol({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 100 100"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={cn('h-10 w-10', className)}
    >
      <circle cx="50" cy="50" r="48" fill="#EAB308" />
      <path
        d="M32 72 L50 28"
        stroke="currentColor"
        strokeWidth="8"
        strokeLinecap="round"
      />
      <path
        d="M68 72 L50 28"
        stroke="currentColor"
        strokeWidth="8"
        strokeLinecap="round"
      />
      <path
        d="M38 56 H62"
        stroke="currentColor"
        strokeWidth="8"
        strokeLinecap="round"
      />
    </svg>
  )
}

export function AxiomDeskFull({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 320 100"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={cn('h-10 w-auto', className)}
    >
      <circle cx="50" cy="50" r="44" fill="#EAB308" />
      <path
        d="M34 70 L50 30"
        stroke="white"
        strokeWidth="8"
        strokeLinecap="round"
      />
      <path
        d="M66 70 L50 30"
        stroke="white"
        strokeWidth="8"
        strokeLinecap="round"
      />
      <path
        d="M40 55 H60"
        stroke="white"
        strokeWidth="8"
        strokeLinecap="round"
      />
      <text
        x="110"
        y="54"
        fill="white"
        className="font-sans font-bold uppercase"
        fontSize="38"
        letterSpacing="0.08em"
        dominantBaseline="middle"
      >
        AXIOMDESK
      </text>
    </svg>
  )
}

export function AxiomDeskWatermark({ className }: { className?: string }) {
  return (
    <AxiomDeskSymbol
      className={cn('h-auto w-full max-w-[420px] text-yellow-500/5', className)}
    />
  )
}

export function AxiomHeroGraphic({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 400 240"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={cn('h-auto w-full max-w-md', className)}
    >
      <rect x="16" y="16" width="368" height="208" rx="16" fill="#18181B" />
      <rect x="40" y="40" width="120" height="80" rx="8" fill="#27272A" />
      <circle cx="80" cy="80" r="20" fill="#EAB308" />
      <rect x="120" y="70" width="24" height="20" rx="4" fill="#3F3F46" />
      <rect x="184" y="40" width="176" height="16" rx="4" fill="#27272A" />
      <rect x="184" y="70" width="120" height="12" rx="3" fill="#3F3F46" />
      <rect x="184" y="94" width="160" height="12" rx="3" fill="#3F3F46" />
      <rect x="40" y="144" width="320" height="12" rx="3" fill="#3F3F46" />
      <rect x="40" y="172" width="260" height="12" rx="3" fill="#3F3F46" />
      <rect x="40" y="200" width="200" height="12" rx="3" fill="#3F3F46" />
    </svg>
  )
}

export function AxiomDeskDashboard({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 260 180"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={cn('h-full w-full', className)}
    >
      <rect width="260" height="180" rx="16" fill="#18181B" />
      <rect x="20" y="20" width="100" height="70" rx="10" fill="#27272A" />
      <circle cx="70" cy="55" r="22" fill="#EAB308" />
      <path d="M150 40 H230" stroke="#3F3F46" strokeWidth="8" strokeLinecap="round" />
      <path d="M150 70 H210" stroke="#3F3F46" strokeWidth="8" strokeLinecap="round" />
      <path d="M150 100 H230" stroke="#3F3F46" strokeWidth="8" strokeLinecap="round" />
      <path
        d="M30 150 L60 130 L90 145 L130 115 L170 125 L210 100 L240 110"
        stroke="#3B82F6"
        strokeWidth="4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export function AxiomDeskAI({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 260 180"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={cn('h-full w-full', className)}
    >
      <rect width="260" height="180" rx="16" fill="#18181B" />
      <rect x="80" y="40" width="100" height="100" rx="22" fill="#27272A" />
      <circle cx="115" cy="90" r="7" fill="#3B82F6" />
      <circle cx="145" cy="90" r="7" fill="#3B82F6" />
      <path d="M130 100 V115" stroke="#3B82F6" strokeWidth="5" strokeLinecap="round" />
      <path d="M130 30 V45" stroke="#3B82F6" strokeWidth="4" strokeLinecap="round" />
      <circle cx="130" cy="25" r="5" fill="#3B82F6" />
      <path d="M100 140 H160" stroke="#3B82F6" strokeWidth="4" strokeLinecap="round" />
      <rect x="100" y="155" width="60" height="6" rx="3" fill="#3B82F6" />
    </svg>
  )
}

export function AxiomDeskSecurity({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 260 180"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={cn('h-full w-full', className)}
    >
      <rect width="260" height="180" rx="16" fill="#18181B" />
      <path
        d="M130 30 L190 62 V110 C190 142 130 165 130 165 C130 165 70 142 70 110 V62 L130 30Z"
        fill="#27272A"
        stroke="#10B981"
        strokeWidth="4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M110 110 L130 130 L160 95"
        stroke="#10B981"
        strokeWidth="7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="130" cy="60" r="12" fill="#10B981" />
      <circle cx="130" cy="60" r="4" fill="#18181B" />
    </svg>
  )
}

export function AxiomDeskIncident({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 260 180"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={cn('h-full w-full', className)}
    >
      <rect width="260" height="180" rx="16" fill="#18181B" />
      <rect x="50" y="35" width="160" height="110" rx="10" fill="#27272A" />
      <path d="M80 70 H180" stroke="#3F3F46" strokeWidth="5" strokeLinecap="round" />
      <path d="M80 95 H160" stroke="#3F3F46" strokeWidth="5" strokeLinecap="round" />
      <path d="M80 120 H140" stroke="#3F3F46" strokeWidth="5" strokeLinecap="round" />
      <circle cx="185" cy="78" r="18" fill="#EAB308" />
      <path d="M178 78 H192" stroke="#18181B" strokeWidth="3" strokeLinecap="round" />
      <path d="M185 71 V85" stroke="#18181B" strokeWidth="3" strokeLinecap="round" />
      <path d="M60 130 H120" stroke="#3B82F6" strokeWidth="4" strokeLinecap="round" />
      <circle cx="130" cy="130" r="3" fill="#3B82F6" />
    </svg>
  )
}
