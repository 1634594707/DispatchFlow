import type { ParkVehicleSnapshot } from './park'

export interface FleetTelemetryPayload {
  parkId: number
  ts: string
  vehicles: ParkVehicleSnapshot[]
}

export interface SSEClientOptions {
  url: string | (() => string | Promise<string>)
  /** Server SSE event name; backend uses `telemetry`. */
  eventName?: string
  onMessage: (data: FleetTelemetryPayload) => void
  onError?: (error: Event) => void
  onOpen?: () => void
  onClose?: () => void
  maxRetries?: number
  baseDelay?: number
  maxDelay?: number
  /**
   * 消息看门狗窗口（毫秒）。若在该窗口内未收到任何消息，则主动重连。
   * 默认 75000ms，用于规避 Cloudflare 100s 超时静默截断。
   */
  keepaliveWindowMs?: number
}

export interface SSEClient {
  start: () => void
  /**
   * 临时停止 SSE 连接（例如切换 parkId 重启流）。
   * 清理 EventSource、看门狗、visibilitychange 监听器，但允许后续通过 start() 恢复。
   */
  stop: () => void
  /**
   * 阶段八 8.2：销毁 SSE 客户端，用于 Vue 组件 onUnmounted 最终清理。
   * 语义上表示"不再使用"，调用后不应再调用 start()。
   * 内部等价于 stop()，但命名更清晰地表达组件卸载时的清理意图。
   */
  destroy: () => void
  isConnected: () => boolean
  getRetryCount: () => number
  /** 重置重试计数并尝试重新连接（用于达到 maxRetries 后的恢复） */
  resetRetryCount: () => void
}
