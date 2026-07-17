import type { FleetTelemetryPayload, SSEClient, SSEClientOptions } from '@/types/stream'
import { registerSSEConnection } from '@/utils/sseConnectionRegistry'

/**
 * 创建 SSE 客户端。
 *
 * P1-1 适配 Cloudflare 100s 超时：
 * - 服务端 proxy_read_timeout 已调整为 90s
 * - 客户端添加「消息看门狗」：若 keepaliveWindowMs 内无任何消息，
 *   主动断开并重连，避免被 Cloudflare 静默截断后长时间无数据。
 * - 配合服务端心跳（FSD_ADMIN_SSE_TIMEOUT_MS=90000）使用。
 */
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

  // 看门狗窗口：默认 75s（小于 Cloudflare 100s + 服务端 90s 超时）
  const keepaliveWindowMs = options.keepaliveWindowMs ?? 75000

  let eventSource: EventSource | null = null
  let retryCount = 0
  let retryTimer: ReturnType<typeof setTimeout> | null = null
  let watchdogTimer: ReturnType<typeof setTimeout> | null = null
  let stopped = false
  let unregister: (() => void) | null = null

  function handlePayload(event: MessageEvent) {
    resetWatchdog()
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

  function clearWatchdog() {
    if (watchdogTimer) {
      clearTimeout(watchdogTimer)
      watchdogTimer = null
    }
  }

  function resetWatchdog() {
    clearWatchdog()
    if (stopped) return
    watchdogTimer = setTimeout(() => {
      if (import.meta.env.DEV) {
        console.warn(`[SSE] ${keepaliveWindowMs}ms 内无消息，主动重连以规避 Cloudflare 超时`)
      }
      // 主动断开后通过 scheduleReconnect 重连
      teardownEventSource()
      scheduleReconnect()
    }, keepaliveWindowMs)
  }

  async function resolveUrl() {
    return typeof url === 'function' ? await url() : url
  }

  async function connect() {
    if (stopped) return

    teardownEventSource()
    clearWatchdog()

    try {
      eventSource = new EventSource(await resolveUrl())

      eventSource.onopen = () => {
        retryCount = 0
        resetWatchdog()
        onOpen?.()
      }

      // 仅通过一种方式监听，避免 onmessage + addEventListener 重复触发导致消息处理两次
      if (eventName) {
        eventSource.addEventListener(eventName, handlePayload as EventListener)
      } else {
        eventSource.onmessage = handlePayload
      }

      eventSource.onerror = (event: Event) => {
        clearWatchdog()
        onError?.(event)

        if (eventSource?.readyState === EventSource.CLOSED) {
          onClose?.()
          scheduleReconnect()
        } else {
          // 连接异常但未关闭，先尝试主动重连
          teardownEventSource()
          scheduleReconnect()
        }
      }
    } catch (e) {
      console.error('[SSE] Connection failed:', e)
      scheduleReconnect()
    }
  }

  function scheduleReconnect() {
    clearWatchdog()
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
      void connect()
    }, waitMs)
  }

  function start() {
    stopped = false
    retryCount = 0
    unregister?.()
    unregister = registerSSEConnection(stop)
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    document.addEventListener('visibilitychange', handleVisibilityChange)
    void connect()
  }

  function stop() {
    stopped = true
    clearWatchdog()
    unregister?.()
    unregister = null
    document.removeEventListener('visibilitychange', handleVisibilityChange)
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

  function resetRetryCount() {
    if (stopped) return
    retryCount = 0
    if (retryTimer) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
    clearWatchdog()
    teardownEventSource()
    void connect()
  }

  function handleVisibilityChange() {
    if (stopped) return
    if (document.visibilityState === 'visible') {
      // 页面重新可见时，若已达 maxRetries 或连接已断开，尝试恢复连接
      if (retryCount >= maxRetries || !isConnected()) {
        resetRetryCount()
      }
    }
  }

  return { start, stop, isConnected, getRetryCount, resetRetryCount }
}
