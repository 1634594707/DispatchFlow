import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { ExceptionAdminListItem } from '@/types/exception'
import type { TaskAdminListItem } from '@/types/task'
import type { ParkLayout, ParkVehicleSnapshot } from '@/types/park'

export interface InterventionQueueResponse {
  pendingCount: number
  manualPendingCount: number
  openExceptionCount: number
  pendingTasks: TaskAdminListItem[]
  manualPendingTasks: TaskAdminListItem[]
  openExceptions: ExceptionAdminListItem[]
}

export interface FleetMetricsResponse {
  assignableVehicleCount: number
  pluggedStandbyCount: number
  chargingCount: number
  onlineVehicleCount: number
}

export interface WorkbenchResponse {
  intervention: InterventionQueueResponse
  fleetMetrics: FleetMetricsResponse
  parkLayout: ParkLayout
  vehicles: ParkVehicleSnapshot[]
}

export function getInterventionQueue() {
  return request.get<any, ApiResponse<InterventionQueueResponse>>('/admin/dispatch/intervention-queue')
}

export function getDispatchWorkbench(parkId?: number) {
  return request.get<any, ApiResponse<WorkbenchResponse>>('/admin/dispatch/workbench', {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function getFleetTelemetryStreamUrl(parkId?: number): string {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const path = '/api/admin/fleet/telemetry/stream'
  const query = parkId != null ? `?parkId=${parkId}` : ''
  return base ? `${base}${path}${query}` : `${path}${query}`
}

export function getDispatchStreamUrl(): string {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const path = '/api/admin/dispatch/stream'
  return base ? `${base}${path}` : path
}
