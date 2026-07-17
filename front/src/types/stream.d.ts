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
  stop: () => void
  isConnected: () => boolean
  getRetryCount: () => number
  /** 重置重试计数并尝试重新连接（用于达到 maxRetries 后的恢复） */
  resetRetryCount: () => void
}
