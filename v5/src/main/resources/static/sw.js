/* Azentro push service worker */

self.addEventListener('push', (event) => {
  let data = { title: 'Azentro', body: 'You have a new notification', url: '/' }
  try {
    if (event.data) {
      data = { ...data, ...event.data.json() }
    }
  } catch (e) {
    // fall back to defaults
  }
  event.waitUntil(
    Promise.all([
      self.registration.showNotification(data.title, {
        body: data.body,
        data: { url: data.url },
        icon: '/logo-light.png',
      }),
      // Tell any open tabs which entity changed so the app can invalidate
      // the affected queries — a push is a real-time refresh signal too.
      clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
        for (const client of clientList) {
          client.postMessage({
            type: 'PUSH_RECEIVED',
            entityType: data.entityType || null,
            entityId: data.entityId || null,
          })
        }
      }),
    ])
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = (event.notification.data && event.notification.data.url) || '/'
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if (client.url.includes(self.location.origin) && 'focus' in client) {
          client.navigate(url)
          return client.focus()
        }
      }
      return clients.openWindow(url)
    })
  )
})
