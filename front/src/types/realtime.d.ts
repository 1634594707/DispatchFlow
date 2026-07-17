import type { DashboardSummary } from '@/types/dashboard'

export interface WorkbenchStreamPayload {
  ts: string
  pendingCount: number
  manualPendingCount: number
  openExceptionCount: number
}

export interface DispatchStreamEventPayload {
  eventType: string
  businessKey?: string
  eventTime?: string
  ts: string
}

export interface ExceptionStreamPayload {
  eventType: string
  businessKey?: string
  payload?: Record<string, unknown>
  eventTime?: string
  ts: string
}

export type StreamHandler<T = unknown> = (payload: T) => void

export interface DispatchStreamHandlers {
  onDashboard?: StreamHandler<DashboardSummary>
  onWorkbench?: StreamHandler<WorkbenchStreamPayload>
  onWorkbenchRefresh?: StreamHandler<{ ts: string }>
  onDashboardRefresh?: StreamHandler<{ ts: string }>
  onException?: StreamHandler<ExceptionStreamPayload>
  onEvent?: StreamHandler<DispatchStreamEventPayload>
  onPing?: StreamHandler<{ ts: string }>
  onOpen?: () => void
  onError?: (event: Event) => void
  onClose?: () => void
}

export interface DispatchStreamClient {
  start: () => void
  /** 临时停止流（例如切换 parkId），可后续 start() 恢复 */
  stop: () => void
  /**
   * 阶段八 8.2：销毁客户端，用于 Vue 组件 onUnmounted 最终清理。
   * 调用后客户端永久不可用，start() 将拒绝执行。
   */
  destroy: () => void
  isConnected: () => boolean
}
