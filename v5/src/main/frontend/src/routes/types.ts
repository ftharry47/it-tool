import type { ReactNode } from 'react'

export interface RouteDefinition {
  path: string
  label: string
  element: ReactNode
  icon?: string
  hidden?: boolean
  group?: string
  children?: RouteDefinition[]
}
