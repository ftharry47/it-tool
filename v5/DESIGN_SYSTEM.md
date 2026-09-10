# ITSM Portal — Design System

## Reference
- Linear.app (density, clarity, fast interactions)
- Jira modern issue view (information hierarchy)
- ServiceNow "Now Experience UX" (enterprise, accessible, dark-first)

## Color Tokens

CSS variables are defined in `src/main/frontend/src/index.css`.

### Dark tokens (CrowdStrike-inspired, default)

| Token | Hex | Usage |
|-------|-----|-------|
| Background | `#0A0A0B` | Page/outer background |
| Foreground | `#FAFAFA` | Primary text |
| Card | `#18181B` | Cards, panels, sidebar surface |
| Popover | `#19191D` | Popover/drawer surfaces |
| Primary | `#DC2626` | Primary buttons, active nav, links, focus rings, brand accents |
| Primary-foreground | `#FAFAFA` | Text on primary buttons |
| Secondary | `#1F1F23` | Secondary section backgrounds |
| Muted | `#222226` | Muted hover backgrounds |
| Muted-foreground | `#A1A1AA` | Secondary labels |
| Accent | `#DC2626` | Focus/hover accents |
| Destructive | `#EF4444` | Delete/cancel/reject actions |
| Border | `#3F3F45` | Card borders and dividers |

The dark background uses a subtle top-centre radial gradient (`hsl(0 70% 12% / 0.12)`) to add depth without changing layout.

### Light tokens

| Token | Hex | Usage |
|-------|-----|-------|
| Background | `#FFFFFF` | Page background |
| Foreground | `#09090B` | Primary text |
| Card | `#FAFAFA` | Cards, panels, sidebar surface |
| Popover | `#FFFFFF` | Popover/drawer surfaces |
| Primary | `#DC2626` | Same red accent |
| Secondary | `#F3F3F5` | Secondary backgrounds |
| Muted | `#F3F3F5` | Muted backgrounds |
| Muted-foreground | `#71717A` | Secondary labels |
| Border | `#E4E4E7` | Light borders |

### Chart palette

Chart colors are stored in `index.css` as CSS variables and consumed by Recharts:

| Token | Hex | Usage |
|-------|-----|-------|
| `--chart-1` | `#DC2626` | Brand red — primary bar/line, "Open" pie segment |
| `--chart-2` | `#52525B` | Slate neutral — secondary category, "Compliant" SLA |
| `--chart-3` | `#D97706` | Muted amber — "In Progress" / warm accent |
| `--chart-4` | `#C2410C` | Deep red-orange — "Closed" / tertiary category |
| `--chart-5` | `#85858C` | Light zinc neutral |
| `--chart-6` | `#A1A1AA` | Off-white/silver contrast |

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
