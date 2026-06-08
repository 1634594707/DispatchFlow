import { ref } from 'vue'
import type { Ref } from 'vue'

export type PushPermission = 'default' | 'granted' | 'denied'

export interface PushPayload {
  title: string
  body?: string
  icon?: string
  badge?: string
  tag?: string
  data?: Record<string, unknown>
}

/**
 * Composable for managing Web Push Notification subscriptions.
 *
 * NOTE: The actual push sending requires a backend with VAPID keys.
 * This composable provides the frontend infrastructure:
 *   - Permission request / status
 *   - Subscription management
 *   - Notification display (for in-app fallback)
 */
export function usePushNotification() {
  const permission: Ref<PushPermission> = ref(
    (typeof Notification !== 'undefined'
      ? Notification.permission
      : 'denied') as PushPermission,
  )

  const subscription: Ref<PushSubscription | null> = ref(null)
  const supported: boolean = 'serviceWorker' in navigator && 'PushManager' in window

  /** Request notification permission from the user */
  async function requestPermission(): Promise<PushPermission> {
    if (!supported) {
      permission.value = 'denied'
      return permission.value
    }
    try {
      const result = await Notification.requestPermission()
      permission.value = result as PushPermission
      return permission.value
    } catch {
      permission.value = 'denied'
      return permission.value
    }
  }

  /**
   * Subscribe to push notifications.
   * @param vapidPublicKey - VAPID public key from the server (Base64 URL-safe)
   */
  async function subscribe(vapidPublicKey?: string): Promise<PushSubscription | null> {
    if (!supported || permission.value !== 'granted') {
      console.warn('[PushNotification] Cannot subscribe: permission not granted')
      return null
    }
    try {
      const registration = await navigator.serviceWorker.ready
      const sub = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: vapidPublicKey,
      })
      subscription.value = sub
      return sub
    } catch (err) {
      console.error('[PushNotification] Subscribe failed:', err)
      return null
    }
  }

  /** Unsubscribe from push notifications */
  async function unsubscribe(): Promise<boolean> {
    if (!subscription.value) return true
    try {
      await subscription.value.unsubscribe()
      subscription.value = null
      return true
    } catch (err) {
      console.error('[PushNotification] Unsubscribe failed:', err)
      return false
    }
  }

  /**
   * Display an in-app notification (fallback when push is not available).
   * Used by SSE event handlers to show notifications even without push.
   */
  function showLocalNotification(payload: PushPayload): void {
    if (permission.value !== 'granted') return
    try {
      const n = new Notification(payload.title, {
        body: payload.body,
        icon: payload.icon || '/icons/icon-192x192.svg',
        badge: payload.badge || '/badge-72x72.svg',
        tag: payload.tag,
        data: payload.data,
        requireInteraction: true,
      })
      n.onclick = () => {
        n.close()
        const url = payload.data?.url as string | undefined
        if (url) {
          window.focus()
          window.location.href = url
        }
      }
    } catch (err) {
      console.warn('[PushNotification] Local notification failed:', err)
    }
  }

  return {
    supported,
    permission,
    subscription,
    requestPermission,
    subscribe,
    unsubscribe,
    showLocalNotification,
  }
}