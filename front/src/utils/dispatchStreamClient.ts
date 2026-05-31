import { getDispatchStreamUrl as buildDispatchStreamPath } from '@/api/dispatch'
import type { DispatchStreamClient, DispatchStreamHandlers } from '@/types/realtime'
import { useAuthStore } from '@/stores/auth'
import { ADMIN_AUTH_ENABLED } from '@/config'
import { registerSSEConnection } from '@/utils/sseConnectionRegistry'

function getDispatchStreamUrl(): string {
  const authStore = useAuthStore()
  const tokenQuery =
    ADMIN_AUTH_ENABLED && authStore.token
      ? `?token=${encodeURIComponent(authStore.token)}`
      : ''
  return `${buildDispatchStreamPath()}${tokenQuery}`
}

export function createDispatchStreamClient(handlers: DispatchStreamHandlers): DispatchStreamClient {
  let eventSource: EventSource | null = null
  let retryCount = 0
  let retryTimer: ReturnType<typeof setTimeout> | null = null
  let stopped = false
  let unregister: (() => void) | null = null
  const maxRetries = 10
  const baseDelay = 1000
  const maxDelay = 30000

  const eventBindings: Array<{ name: string; handler: EventListener }> = []

  function parsePayload<T>(event: MessageEvent): T | null {
    try {
      return JSON.parse(event.data) as T
    } catch (e) {
      console.error('[DispatchStream] Failed to parse message:', e)
      return null
    }
  }

  function teardownEventSource() {
    if (!eventSource) return
    eventSource.onopen = null
    eventSource.onerror = null
    for (const { name, handler } of eventBindings) {
      eventSource.removeEventListener(name, handler)
    }
    eventBindings.length = 0
    eventSource.close()
    eventSource = null
  }

  function addTypedListener<T>(name: string, handler: (payload: T) => void) {
    const listener: EventListener = (event) => {
      const payload = parsePayload<T>(event as MessageEvent)
      if (payload !== null) {
        handler(payload)
      }
    }
    eventSource!.addEventListener(name, listener)
    eventBindings.push({ name, handler: listener })
  }

  function scheduleReconnect() {
    if (stopped || retryCount >= maxRetries) {
      handlers.onClose?.()
      return
    }
    const delay = Math.min(baseDelay * Math.pow(2, retryCount), maxDelay)
    retryTimer = setTimeout(() => {
      retryCount++
      connect()
    }, delay)
  }

  function connect() {
    if (stopped) return
    teardownEventSource()
    eventSource = new EventSource(getDispatchStreamUrl())

    eventSource.onopen = () => {
      retryCount = 0
      handlers.onOpen?.()
    }

    eventSource.onerror = (event) => {
      handlers.onError?.(event)
      if (eventSource?.readyState === EventSource.CLOSED) {
        handlers.onClose?.()
        scheduleReconnect()
      }
    }

    addTypedListener('dashboard', (payload) => handlers.onDashboard?.(payload as never))
    addTypedListener('workbench', (payload) => handlers.onWorkbench?.(payload as never))
    addTypedListener('workbench-refresh', (payload) => handlers.onWorkbenchRefresh?.(payload as never))
    addTypedListener('dashboard-refresh', (payload) => handlers.onDashboardRefresh?.(payload as never))
    addTypedListener('exception', (payload) => handlers.onException?.(payload as never))
    addTypedListener('event', (payload) => handlers.onEvent?.(payload as never))
    addTypedListener('ping', (payload) => handlers.onPing?.(payload as never))
  }

  function start() {
    stopped = false
    retryCount = 0
    unregister?.()
    unregister = registerSSEConnection(stop)
    connect()
  }

  function stop() {
    stopped = true
    unregister?.()
    unregister = null
    if (retryTimer) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
    teardownEventSource()
    handlers.onClose?.()
  }

  function isConnected() {
    return eventSource?.readyState === EventSource.OPEN
  }

  return { start, stop, isConnected }
}
