import request from '@/utils/request'
import type { ApiResponse, PageResponse } from '@/types/api'
import type { ExceptionAdminListItem } from '@/types/exception'
import type { TaskAdminListItem } from '@/types/task'
import type { ParkLayout, ParkVehicleSnapshot } from '@/types/park'
export type TaskPoolFilter = 'ALL' | 'PENDING' | 'MANUAL_PENDING'

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

export function queryTaskPool(params: {
  parkId?: number
  poolStatus: TaskPoolFilter
  pageNo: number
  pageSize: number
}) {
  const poolStatus =
    params.poolStatus === 'ALL' ? 'ALL_POOL' : params.poolStatus
  return request.post<any, ApiResponse<PageResponse<TaskAdminListItem>>>(
    '/admin/dispatch/task-pool/query',
    {
      parkId: params.parkId,
      poolStatus,
      pageNo: params.pageNo,
      pageSize: params.pageSize,
    },
  )
}

export function getFleetTelemetryStreamUrl(parkId?: number): string {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const path = '/api/admin/fleet/telemetry/stream'
  const params = new URLSearchParams()
  if (parkId != null) params.set('parkId', String(parkId))
  const token = localStorage.getItem('fsd_admin_token')
  if (token) params.set('token', token)
  const query = params.toString() ? `?${params.toString()}` : ''
  return base ? `${base}${path}${query}` : `${path}${query}`
}

export function getDispatchStreamUrl(): string {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const path = '/api/admin/dispatch/stream'
  return base ? `${base}${path}` : path
}
