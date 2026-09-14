import type { QueryClient } from '@tanstack/react-query'

// Prefix-level invalidation for work-item data. Push arrivals and
// in-app notification bumps call this so open views refresh without a
// manual reload or navigation. invalidateQueries refetches only
// currently-active queries; inactive ones are marked stale for next mount.
const ALL_WORK_PREFIXES = [
  'notifications',
  'incidents', 'incident', 'incident-activity', 'incident-comments',
  'service-requests', 'service-request', 'service-request-activity',
  'problems', 'problem',
  'changes', 'change',
  'dashboard',
  'sla-instances',
] as const

const BY_ENTITY_TYPE: Record<string, readonly string[]> = {
  INCIDENT: ['notifications', 'incidents', 'incident', 'incident-activity', 'incident-comments', 'dashboard', 'sla-instances'],
  SERVICE_REQUEST: ['notifications', 'service-requests', 'service-request', 'service-request-activity', 'dashboard', 'sla-instances'],
  PROBLEM: ['notifications', 'problems', 'problem', 'dashboard'],
  CHANGE: ['notifications', 'changes', 'change', 'dashboard'],
}

export function invalidateWorkQueries(queryClient: QueryClient, entityType?: string | null) {
  const prefixes = (entityType && BY_ENTITY_TYPE[entityType]) || ALL_WORK_PREFIXES
  for (const prefix of prefixes) {
    queryClient.invalidateQueries({ queryKey: [prefix] })
  }
}

// sw.js postMessages PUSH_RECEIVED on every push arrival — the open tab
// invalidates the affected queries immediately, even when backgrounded.
export function listenForPush(queryClient: QueryClient) {
  if (!('serviceWorker' in navigator)) return
  navigator.serviceWorker.addEventListener('message', (event) => {
    if (event.data?.type === 'PUSH_RECEIVED') {
      invalidateWorkQueries(queryClient, event.data.entityType ?? null)
    }
  })
}
