import { getDispatchStreamUrl as buildDispatchStreamPath } from '@/api/dispatch'
import type { DispatchStreamClient, DispatchStreamHandlers } from '@/types/realtime'
import { useAuthStore } from '@/stores/auth'
import { ADMIN_AUTH_ENABLED } from '@/config'

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
  const maxRetries = 10
  const baseDelay = 1000
  const maxDelay = 30000

  function parsePayload<T>(event: MessageEvent): T | null {
    try {
      return JSON.parse(event.data) as T
    } catch (e) {
      console.error('[DispatchStream] Failed to parse message:', e)
      return null
    }
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

    eventSource.addEventListener('dashboard', (event) => {
      const payload = parsePayload(event)
      if (payload) handlers.onDashboard?.(payload as never)
    })
    eventSource.addEventListener('workbench', (event) => {
      const payload = parsePayload(event)
      if (payload) handlers.onWorkbench?.(payload as never)
    })
    eventSource.addEventListener('workbench-refresh', (event) => {
      const payload = parsePayload(event)
      if (payload) handlers.onWorkbenchRefresh?.(payload as never)
    })
    eventSource.addEventListener('dashboard-refresh', (event) => {
      const payload = parsePayload(event)
      if (payload) handlers.onDashboardRefresh?.(payload as never)
    })
    eventSource.addEventListener('exception', (event) => {
      const payload = parsePayload(event)
      if (payload) handlers.onException?.(payload as never)
    })
    eventSource.addEventListener('event', (event) => {
      const payload = parsePayload(event)
      if (payload) handlers.onEvent?.(payload as never)
    })
    eventSource.addEventListener('ping', (event) => {
      const payload = parsePayload(event)
      if (payload) handlers.onPing?.(payload as never)
    })
  }

  function start() {
    stopped = false
    retryCount = 0
    connect()
  }

  function stop() {
    stopped = true
    if (retryTimer) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
    eventSource?.close()
    eventSource = null
    handlers.onClose?.()
  }

  function isConnected() {
    return eventSource?.readyState === EventSource.OPEN
  }

  return { start, stop, isConnected }
}
