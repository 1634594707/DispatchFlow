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

export function getParkOrders() {
  return request.get<any, ApiResponse<ParkOrderSnapshot[]>>('/admin/park/orders')
}

export function createParkOrder(data: ParkOrderCreateRequest) {
  return request.post<any, ApiResponse<ParkOrderCreateResponse>>('/admin/park/orders', data)
}
