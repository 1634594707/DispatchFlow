import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type {
  ParkLayout,
  ParkOrderCreateRequest,
  ParkOrderCreateResponse,
  ParkOrderSnapshot,
  ParkStation,
  ParkSummary,
  ParkVehicleSnapshot,
} from '@/types/park'

export function listParks() {
  return request.get<any, ApiResponse<ParkSummary[]>>('/admin/parks')
}

export function getParkLayout(parkId?: number) {
  return request.get<any, ApiResponse<ParkLayout>>('/admin/park/layout', {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function getParkStations(parkId?: number) {
  return request.get<any, ApiResponse<ParkStation[]>>('/admin/park/stations', {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function getParkVehicles() {
  return request.get<any, ApiResponse<ParkVehicleSnapshot[]>>('/admin/park/vehicles')
}

export function getParkGeofences(parkId?: number) {
  return request.get<any, ApiResponse<import('@/types/park').ParkGeofence[]>>('/admin/park/geofences', {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function getParkOverview() {
  return request.get<any, ApiResponse<import('@/types/park').ParkOverviewItem[]>>('/admin/park/overview')
}

export function getParkOrders() {
  return request.get<any, ApiResponse<ParkOrderSnapshot[]>>('/admin/park/orders')
}

export function createParkOrder(data: ParkOrderCreateRequest, mobileApiKey?: string) {
  const key = mobileApiKey || localStorage.getItem('fsd_mobile_api_key') || undefined
  return request.post<any, ApiResponse<ParkOrderCreateResponse>>('/admin/park/orders', data, {
    headers: key ? { 'X-Mobile-Api-Key': key } : undefined,
  })
}
