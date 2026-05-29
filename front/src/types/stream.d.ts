import type { ParkVehicleSnapshot } from './park'

export interface FleetTelemetryPayload {
  parkId: number
  ts: string
  vehicles: ParkVehicleSnapshot[]
}

export interface SSEClientOptions {
  url: string
  /** Server SSE event name; backend uses `telemetry`. */
  eventName?: string
  onMessage: (data: FleetTelemetryPayload) => void
  onError?: (error: Event) => void
  onOpen?: () => void
  onClose?: () => void
  maxRetries?: number
  baseDelay?: number
  maxDelay?: number
}

export interface SSEClient {
  start: () => void
  stop: () => void
  isConnected: () => boolean
  getRetryCount: () => number
}
