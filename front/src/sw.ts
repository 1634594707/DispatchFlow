/// <reference lib="webworker" />
import { cleanupOutdatedCaches, createHandlerBoundToURL, precacheAndRoute } from 'workbox-precaching'
import { NavigationRoute, registerRoute } from 'workbox-routing'
import { NetworkFirst, StaleWhileRevalidate } from 'workbox-strategies'
import { ExpirationPlugin } from 'workbox-expiration'

declare const self: ServiceWorkerGlobalScope

// Precache static assets
precacheAndRoute(self.__WB_MANIFEST)
cleanupOutdatedCaches()

// SPA fallback — serve index.html for all navigation requests
const handler = createHandlerBoundToURL('/index.html')
const navigationRoute = new NavigationRoute(handler, {
  denylist: [/^\/api\//],
})
registerRoute(navigationRoute)

// API cache: NetworkFirst with timeout
registerRoute(
  /^\/api\/.*/i,
  new NetworkFirst({
    cacheName: 'api-cache',
    plugins: [
      new ExpirationPlugin({
        maxEntries: 100,
        maxAgeSeconds: 60 * 60 * 24, // 1 day
      }),
    ],
    networkTimeoutSeconds: 10,
  }),
)

// Static assets: StaleWhileRevalidate
registerRoute(
  /\.(?:js|css|svg|png|ico|woff2)$/,
  new StaleWhileRevalidate({
    cacheName: 'static-assets',
    plugins: [
      new ExpirationPlugin({
        maxEntries: 100,
        maxAgeSeconds: 60 * 60 * 24 * 30, // 30 days
      }),
    ],
  }),
)

// ── Push Notifications ──────────────────────────────────────────

self.addEventListener('push', (event) => {
  if (!event.data) return

  try {
    const data = event.data.json()
    const { title, body, icon, badge, tag, data: payload } = data

    const options: NotificationOptions = {
      body: body || '',
      icon: icon || '/icons/icon-192x192.svg',
      badge: badge || '/badge-72x72.svg',
      tag: tag || 'dispatchflow-notification',
      vibrate: [200, 100, 200],
      requireInteraction: true,
      data: payload || {},
    }

    event.waitUntil(
      self.registration.showNotification(title || 'DispatchFlow', options),
    )
  } catch {
    // Fallback: show raw text if not JSON
    event.waitUntil(
      self.registration.showNotification(event.data.text(), {
        icon: '/icons/icon-192x192.svg',
        badge: '/badge-72x72.svg',
      }),
    )
  }
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()

  const clickTarget = event.notification.data?.url || '/workbench'

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      // Focus existing tab if available
      for (const client of clientList) {
        if (client.url.includes(self.location.origin) && 'focus' in client) {
          client.postMessage({ type: 'NOTIFICATION_CLICK', data: event.notification.data })
          return client.focus()
        }
      }
      // Open new tab
      return self.clients.openWindow(clickTarget)
    }),
  )
})

self.addEventListener('message', (event) => {
  if (event.data?.type === 'SKIP_WAITING') {
    self.skipWaiting()
  }
})

export {}