import request from '@/utils/request'
import type { ApiResponse, PageResponse } from '@/types/api'
import type { VehicleQueryRequest, VehicleAdminListItem, VehicleDetailResponse } from '@/types/vehicle'

export function getVehicleList() {
  return request.get<any, ApiResponse<VehicleAdminListItem[]>>('/admin/vehicles')
}

export function queryVehicles(data: VehicleQueryRequest) {
  return request.post<any, ApiResponse<PageResponse<VehicleAdminListItem>>>('/admin/vehicles/query', data)
}

export function getVehicleDetail(vehicleId: number) {
  return request.get<any, ApiResponse<VehicleDetailResponse>>(`/admin/vehicles/${vehicleId}`)
}

export interface VehicleUpsertPayload {
  vehicleCode: string
  vehicleName: string
  vehicleType?: string
  linkMode?: string
  remark?: string
}

export interface VehicleCredential {
  id: number
  vehicleId: number
  apiKey: string
  status: string
  createdAt?: string
}

export interface VehicleMaintenanceRecord {
  id: number
  vehicleId: number
  vehicleCode?: string
  maintenanceType: string
  description: string
  maintenanceAt: string
  operatorName?: string
  status: string
  remark?: string
  createdAt?: string
}

export function createVehicle(payload: VehicleUpsertPayload) {
  return request.post<any, ApiResponse<VehicleDetailResponse>>('/admin/vehicles/manage', payload)
}

export function updateVehicle(vehicleId: number, payload: VehicleUpsertPayload) {
  return request.put<any, ApiResponse<VehicleDetailResponse>>(`/admin/vehicles/manage/${vehicleId}`, payload)
}

export function disableVehicle(vehicleId: number) {
  return request.post<any, ApiResponse<void>>(`/admin/vehicles/manage/${vehicleId}/disable`)
}

export function fetchVehicleCredentials(vehicleId: number) {
  return request.get<any, ApiResponse<VehicleCredential[]>>(`/admin/vehicles/manage/${vehicleId}/credentials`)
}

export function createVehicleCredential(vehicleId: number) {
  return request.post<any, ApiResponse<VehicleCredential>>(`/admin/vehicles/manage/${vehicleId}/credentials`)
}

export function disableVehicleCredential(credentialId: number) {
  return request.post<any, ApiResponse<void>>(`/admin/vehicles/manage/credentials/${credentialId}/disable`)
}

export function fetchVehicleMaintenance(vehicleId: number) {
  return request.get<any, ApiResponse<VehicleMaintenanceRecord[]>>(`/admin/vehicles/manage/${vehicleId}/maintenance`)
}

export interface VehicleHealth {
  vehicleId: number
  vehicleCode: string
  healthScore: number
  healthLevel: string
  openExceptionCount: number
  failedTaskCount: number
  maintenanceCount: number
  suggestions: string[]
}

export interface VehicleTrajectoryPoint {
  ts?: string
  x: number
  y: number
  soc?: number
}

export function fetchVehicleHealth(vehicleId: number) {
  return request.get<any, ApiResponse<VehicleHealth>>(`/admin/vehicles/manage/${vehicleId}/health`)
}

export function fetchVehicleTrajectory(
  vehicleId: number,
  opts?: { source?: string; from?: string; to?: string },
) {
  return request.get<any, ApiResponse<VehicleTrajectoryPoint[]>>(
    `/admin/vehicles/manage/${vehicleId}/trajectory`,
    { params: opts },
  )
}

export interface VehicleTrajectoryDwell {
  startTime?: string
  endTime?: string
  x: number
  y: number
  durationMinutes: number
}

export function fetchVehicleTrajectoryDwell(
  vehicleId: number,
  opts?: { from?: string; to?: string },
) {
  return request.get<any, ApiResponse<VehicleTrajectoryDwell[]>>(
    `/admin/vehicles/manage/${vehicleId}/trajectory/dwell`,
    { params: opts },
  )
}

export function createVehicleMaintenance(payload: {
  vehicleId: number
  maintenanceType: string
  description: string
  maintenanceAt: string
  status?: string
  remark?: string
}) {
  return request.post<any, ApiResponse<VehicleMaintenanceRecord>>('/admin/vehicles/manage/maintenance', payload)
}
