import type { FleetTelemetryPayload, SSEClient, SSEClientOptions } from '@/types/stream'
import { registerSSEConnection } from '@/utils/sseConnectionRegistry'

export function createSSEClient(options: SSEClientOptions): SSEClient {
  const {
    url,
    eventName = 'telemetry',
    onMessage,
    onError,
    onOpen,
    onClose,
    maxRetries = 10,
    baseDelay = 1000,
    maxDelay = 30000,
  } = options

  let eventSource: EventSource | null = null
  let retryCount = 0
  let retryTimer: ReturnType<typeof setTimeout> | null = null
  let stopped = false
  let unregister: (() => void) | null = null

  function handlePayload(event: MessageEvent) {
    try {
      const data: FleetTelemetryPayload = JSON.parse(event.data)
      onMessage(data)
    } catch (e) {
      console.error('[SSE] Failed to parse message:', e)
    }
  }

  function teardownEventSource() {
    if (!eventSource) return
    eventSource.onopen = null
    eventSource.onmessage = null
    eventSource.onerror = null
    if (eventName) {
      eventSource.removeEventListener(eventName, handlePayload as EventListener)
    }
    eventSource.close()
    eventSource = null
  }

  function connect() {
    if (stopped) return

    teardownEventSource()

    try {
      eventSource = new EventSource(url)

      eventSource.onopen = () => {
        retryCount = 0
        onOpen?.()
      }

      eventSource.onmessage = handlePayload
      if (eventName) {
        eventSource.addEventListener(eventName, handlePayload as EventListener)
      }

      eventSource.onerror = (event: Event) => {
        onError?.(event)

        if (eventSource?.readyState === EventSource.CLOSED) {
          onClose?.()
          scheduleReconnect()
        }
      }
    } catch (e) {
      console.error('[SSE] Connection failed:', e)
      scheduleReconnect()
    }
  }

  function scheduleReconnect() {
    if (stopped || retryCount >= maxRetries) {
      console.warn(`[SSE] Max retries (${maxRetries}) reached or client stopped`)
      onClose?.()
      return
    }

    const delay = Math.min(baseDelay * Math.pow(2, retryCount), maxDelay)
    const jitter = delay * 0.1 * Math.random()
    const waitMs = delay + jitter
    if (import.meta.env.DEV) {
      console.debug(`[SSE] Reconnecting in ${Math.round(waitMs)}ms (attempt ${retryCount + 1}/${maxRetries})`)
    }

    retryTimer = setTimeout(() => {
      retryCount++
      connect()
    }, waitMs)
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
    onClose?.()
  }

  function isConnected(): boolean {
    return eventSource?.readyState === EventSource.OPEN
  }

  function getRetryCount(): number {
    return retryCount
  }

  return { start, stop, isConnected, getRetryCount }
}
