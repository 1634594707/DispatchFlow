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

export interface AdminSseTicketResponse {
  ticket: string
  expiresInSeconds: number
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

export function issueAdminSseTicket() {
  return request.post<any, ApiResponse<AdminSseTicketResponse>>('/admin/sse-ticket')
}

export async function getFleetTelemetryStreamUrl(parkId?: number): Promise<string> {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const path = '/api/admin/fleet/telemetry/stream'
  const params = new URLSearchParams()
  if (parkId != null) params.set('parkId', String(parkId))
  params.set('ticket', (await issueAdminSseTicket()).data.ticket)
  const query = params.toString() ? `?${params.toString()}` : ''
  return base ? `${base}${path}${query}` : `${path}${query}`
}

export async function getDispatchStreamUrl(): Promise<string> {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const path = '/api/admin/dispatch/stream'
  const ticket = (await issueAdminSseTicket()).data.ticket
  const query = `?ticket=${encodeURIComponent(ticket)}`
  return base ? `${base}${path}${query}` : `${path}${query}`
}
