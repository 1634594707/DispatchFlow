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
