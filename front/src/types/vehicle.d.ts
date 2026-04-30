import type { OnlineStatus, DispatchStatus } from '@/constants/enums'

export interface VehicleQueryRequest {
  vehicleCode?: string
  onlineStatus?: OnlineStatus
  dispatchStatus?: DispatchStatus
  pageNo: number
  pageSize: number
}

export interface VehicleAdminListItem {
  vehicleId: number
  vehicleCode: string
  vehicleName: string
  onlineStatus: OnlineStatus
  dispatchStatus: DispatchStatus
  currentTaskId: number | null
  currentOrderId: number | null
  currentLatitude: number | null
  currentLongitude: number | null
  batteryLevel: number
  lastReportTime: string
}

export interface VehicleDetailResponse {
  vehicleId: number
  vehicleCode: string
  vehicleName: string
  vehicleType: string
  onlineStatus: OnlineStatus
  dispatchStatus: DispatchStatus
  currentTaskId: number | null
  currentOrderId: number | null
  currentLatitude: number
  currentLongitude: number
  batteryLevel: number
  lastReportTime: string
  remark: string | null
}
