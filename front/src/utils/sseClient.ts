import type { FleetTelemetryPayload, SSEClient, SSEClientOptions } from '@/types/stream'

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

  function calculateDelay(): number {
    const delay = Math.min(baseDelay * Math.pow(2, retryCount), maxDelay)
    const jitter = delay * 0.1 * Math.random()
    return delay + jitter
  }

  function handlePayload(event: MessageEvent) {
    try {
      const data: FleetTelemetryPayload = JSON.parse(event.data)
      onMessage(data)
    } catch (e) {
      console.error('[SSE] Failed to parse message:', e)
    }
  }

  function connect() {
    if (stopped) return

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

    const delay = calculateDelay()
    console.log(`[SSE] Reconnecting in ${Math.round(delay)}ms (attempt ${retryCount + 1}/${maxRetries})`)

    retryTimer = setTimeout(() => {
      retryCount++
      connect()
    }, delay)
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
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
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
