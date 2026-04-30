import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type {
  ParkLayout,
  ParkOrderCreateRequest,
  ParkOrderCreateResponse,
  ParkOrderSnapshot,
  ParkStation,
  ParkVehicleSnapshot,
} from '@/types/park'

export function getParkLayout() {
  return request.get<any, ApiResponse<ParkLayout>>('/admin/park/layout')
}

export function getParkStations() {
  return request.get<any, ApiResponse<ParkStation[]>>('/admin/park/stations')
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
