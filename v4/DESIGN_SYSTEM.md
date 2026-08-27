# ITSM Portal — Design System

## Reference
- Linear.app (density, clarity, fast interactions)
- Jira modern issue view (information hierarchy)
- ServiceNow "Now Experience UX" (enterprise, accessible, dark-first)

## Color Tokens

CSS variables are defined in `src/main/frontend/src/index.css`.

### Light mode
- Background: `#ffffff`
- Foreground: `#0a0a0a`
- Card: `#ffffff`
- Primary: `#2c3e6b` (deep blue)
- Secondary: `#f4f4f5`
- Muted: `#f4f4f5`
- Muted-foreground: `#6b6b74`
- Accent: `#f4f4f5`
- Destructive: `#c61c1c`
- Border: `#4d4d56`

### Dark mode
- Background: `#000000`
- Foreground: `#fafafa`
- Card: `#080808`
- Primary: `#fafafa`
- Secondary: `#242424`
- Muted: `#242424`
- Muted-foreground: `#a1a1aa`
- Accent: `#242424`
- Destructive: `#c61c1c`
- Border: `#616161`

## Spacing Scale (4-point grid)
- 0.25rem (4px), 0.5rem (8px), 0.75rem (12px), 1rem (16px)
- 1.5rem (24px), 2rem (32px), 2.5rem (40px), 3rem (48px)
- 4rem (64px), 6rem (96px), 8rem (128px)

## Type Scale
- xs: 0.75rem / 1rem line-height
- sm: 0.875rem / 1.25rem
- base: 1rem / 1.5rem
- lg: 1.125rem / 1.75rem
- xl: 1.25rem / 1.75rem
- 2xl: 1.5rem / 2rem
- 3xl: 1.875rem / 2.25rem

Font family: `Inter` for UI, `SF Mono` / `JetBrains Mono` for code.

## Elevation
- Level 0: none
- Level 1: `0 1px 2px rgba(0,0,0,0.05)`
- Level 2: `0 4px 6px -1px rgba(0,0,0,0.1)`
- Level 3: `0 10px 15px -3px rgba(0,0,0,0.1)`

## Motion
- Transition base: `150ms cubic-bezier(0.4, 0, 0.2, 1)`
- Hover/background: `150ms`
- Modal/overlay: `200ms`
- Loading skeletons: `1.5s ease-in-out infinite`
- Respect `prefers-reduced-motion`.

## Components (planned)
- Buttons: primary, secondary, ghost, destructive, loading state
- Inputs: text, select, textarea, search, checkbox, switch
- Cards: standard with header/body/footer, compact list rows
- Tables: TanStack Table with sorting, filtering, pagination
- Badges: status, priority, SLA
- Modals / Dialogs
- Toasts / notifications
- Command palette
