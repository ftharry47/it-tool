const CACHE_NAME = 'it-support-portal-v3-enterprise';
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/dashboard',
  '/login',
  '/settings',
  '/manifest.json',
  '/icon.svg',
  '/kb',
  '/csat',
  '/audit',
  '/admin',
  '/analytics',
  '/cmdb',
  '/import-export',
  '/help-bot',
  '/offline.html'
];

self.addEventListener('install', event => {
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.map(key => caches.delete(key)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  const { request } = event;
  const url = new URL(request.url);

  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/uploads/')) {
    return;
  }

  if (request.method !== 'GET') {
    return;
  }

  // Network-first for all requests to ensure new builds load instantly
  event.respondWith(
    fetch(request)
      .then(response => {
        if (response.status === 200) {
          const clone = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(request, clone)).catch(() => {});
        }
        return response;
      })
      .catch(() => {
        return caches.match(request).then(cached => {
          if (cached) return cached;
          if (request.mode === 'navigate' || request.destination === 'document') {
            return caches.match('/offline.html');
          }
        });
      })
  );
});
