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
    self.registration.showNotification(data.title, {
      body: data.body,
      data: { url: data.url },
      icon: '/logo-light.png',
    })
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
