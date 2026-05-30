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
  stop: () => void
  isConnected: () => boolean
}
