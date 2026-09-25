import { getDispatchStreamUrl as buildDispatchStreamPath } from '@/api/dispatch'
import type { DispatchStreamClient, DispatchStreamHandlers } from '@/types/realtime'
import { registerSSEConnection } from '@/utils/sseConnectionRegistry'

function getDispatchStreamUrl(parkId?: number): Promise<string> {
  return buildDispatchStreamPath(parkId)
}

export function createDispatchStreamClient(handlers: DispatchStreamHandlers, parkId?: number): DispatchStreamClient {
  let eventSource: EventSource | null = null
  let retryCount = 0
  let retryTimer: ReturnType<typeof setTimeout> | null = null
  let stopped = false
  /** 阶段八 8.2：destroy 后永久不可用 */
  let destroyed = false
  let unregister: (() => void) | null = null
  let onVisibility: (() => void) | null = null
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
    if (stopped) {
      return
    }
    // §6.3：不再"重连 10 次即永久放弃"——持续以指数退避重连（延迟封顶 maxDelay），
    // 断线由界面通过 handlers.onError/onClose 呈现为"数据已停止更新"，而非静默失联。
    const exp = Math.min(retryCount, 15)
    const delay = Math.min(baseDelay * Math.pow(2, exp), maxDelay)
    retryTimer = setTimeout(() => {
      retryCount++
      void connect()
    }, delay)
  }

  async function connect() {
    if (stopped) return
    teardownEventSource()
    eventSource = new EventSource(await getDispatchStreamUrl(parkId))

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
    if (destroyed) {
      console.warn('[DispatchStream] Cannot start: client has been destroyed. Create a new client instead.')
      return
    }
    stopped = false
    retryCount = 0
    unregister?.()
    unregister = registerSSEConnection(stop)
    // §6.3：后台标签页会被浏览器节流/断开 SSE；回到前台时若已断开则立即重连（重置退避）。
    if (typeof document !== 'undefined' && !onVisibility) {
      onVisibility = () => {
        if (document.visibilityState === 'visible' && !stopped && !isConnected()) {
          if (retryTimer) {
            clearTimeout(retryTimer)
            retryTimer = null
          }
          retryCount = 0
          void connect()
        }
      }
      document.addEventListener('visibilitychange', onVisibility)
    }
    void connect()
  }

  function stop() {
    stopped = true
    unregister?.()
    unregister = null
    if (onVisibility && typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', onVisibility)
      onVisibility = null
    }
    if (retryTimer) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
    teardownEventSource()
    handlers.onClose?.()
  }

  /**
   * 阶段八 8.2：销毁客户端，用于 Vue 组件 onUnmounted 最终清理。
   * 调用后客户端永久不可用，start() 将拒绝执行。
   */
  function destroy() {
    destroyed = true
    stop()
  }

  function isConnected() {
    return eventSource?.readyState === EventSource.OPEN
  }

  return { start, stop, destroy, isConnected }
}
