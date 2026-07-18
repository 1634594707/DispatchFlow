import type { OnlineStatus, DispatchStatus } from '@/constants/enums'

/** 车辆配送区域：地理配送 / 园区内部 / 通用 */
export type VehicleDeliveryZone = 'GEO_DELIVERY' | 'SCHEMATIC' | 'BOTH'

export interface VehicleQueryRequest {
  vehicleCode?: string
  onlineStatus?: OnlineStatus
  dispatchStatus?: DispatchStatus
  deliveryZone?: VehicleDeliveryZone
  parkId?: number
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
  deliveryZone?: VehicleDeliveryZone
  maxLoadCapacity?: number | null
  currentLoad?: number | null
  /** P2-5: 车辆宽度（厘米），用于道路宽度可用性检查 */
  widthCm?: number | null
  /** P2-5: 车辆长度（厘米） */
  lengthCm?: number | null
  /** P2-5: 最小转弯半径（米），用于窄路/急弯过滤 */
  turningRadiusM?: number | null
  /** P2-5: 允许道路等级（逗号分隔，NULL=全部；如 ARTERIAL,SECONDARY） */
  allowedRoadClasses?: string | null
  lastReportTime: string
}

export interface VehicleDetailResponse {
  vehicleId: number
  vehicleCode: string
  vehicleName: string
  vehicleType: string
  linkMode?: 'SIM' | 'REAL' | 'VDA5050'
  vdaManufacturer?: string | null
  vdaSerialNumber?: string | null
  vdaInterfaceName?: string | null
  onlineStatus: OnlineStatus
  dispatchStatus: DispatchStatus
  currentTaskId: number | null
  currentOrderId: number | null
  currentLatitude: number
  currentLongitude: number
  batteryLevel: number
  deliveryZone?: VehicleDeliveryZone
  maxLoadCapacity?: number | null
  currentLoad?: number | null
  /** P2-5: 车辆宽度（厘米），用于道路宽度可用性检查 */
  widthCm?: number | null
  /** P2-5: 车辆长度（厘米） */
  lengthCm?: number | null
  /** P2-5: 最小转弯半径（米），用于窄路/急弯过滤 */
  turningRadiusM?: number | null
  /** P2-5: 允许道路等级（逗号分隔，NULL=全部；如 ARTERIAL,SECONDARY） */
  allowedRoadClasses?: string | null
  lastReportTime: string
  remark: string | null
}
