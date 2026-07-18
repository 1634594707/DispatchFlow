/** P2-1: 轨迹点类型，用于在画布上区分规划/实际/预测/历史轨迹的颜色与样式 */
export type TrajectoryPointType = 'PLAN' | 'ACTUAL' | 'PREDICTED' | 'HISTORY'

export interface ParkPoint {
  code: string
  x: number
  y: number
  longitude?: number | null
  latitude?: number | null
  /** P2-1: 轨迹点类型，未设置时由调用方按所属字段（trajectory / plannedRouteGeo / geoTrajectory）推断 */
  type?: TrajectoryPointType
}

export interface ParkSummary {
  parkId: number
  parkCode: string
  parkName: string
  mapWidth?: number
  mapHeight?: number
  defaultPark: boolean
}

/** 站点配送区域：地理配送 / 园区内部 / 通用 */
export type StationDeliveryZone = 'GEO_DELIVERY' | 'SCHEMATIC' | 'GENERAL'

export interface ParkStation {
  parkId: number
  parkCode: string
  stationId: number
  stationCode: string
  stationName: string
  stationType?: string
  x: number
  y: number
  coordLng?: number | null
  coordLat?: number | null
  area: string
  deliveryZone?: StationDeliveryZone
}

export interface ParkRoadNode {
  code: string
  x: number
  y: number
}

export interface ParkRoadSegment {
  from: string
  to: string
}

export interface ParkLayout {
  enabled: boolean
  parkId?: number
  parkCode?: string
  parkName?: string
  width: number
  height: number
  minZoom: number
  maxZoom: number
  vehicleSpeedPxPerSecond: number
  centerLng?: number | null
  centerLat?: number | null
  mapProvider?: string | null
  xFieldAlias: string
  yFieldAlias: string
  stations: ParkStation[]
  parkingSpots: ParkPoint[]
  roadNodes: ParkRoadNode[]
  roadSegments: ParkRoadSegment[]
}

export interface ParkVehicleSnapshot {
  vehicleId: number
  vehicleCode: string
  vehicleName: string
  onlineStatus: string
  dispatchStatus: string
  currentTaskId: number | null
  currentOrderId: number | null
  batteryLevel: number
  batteryStatus?: 'NORMAL' | 'LOW' | 'CRITICAL' | 'CHARGING'
  x: number
  y: number
  longitude?: number | null
  latitude?: number | null
  heading?: number | null
  lastTelemetryAt?: string | null
  telemetryStale?: boolean
  runtimeStage: string
  targetCode: string | null
  targetType: string | null
  charging: boolean
  lowBattery: boolean
  linkMode: 'SIM' | 'REAL' | 'VDA5050'
  deliveryZone?: 'GEO_DELIVERY' | 'SCHEMATIC' | 'BOTH'
  maxLoadCapacity?: number | null
  currentLoad?: number | null
  trajectory: ParkPoint[]
  geoTrajectory?: ParkPoint[]
  plannedRouteGeo?: ParkPoint[]
  routeSource?: string | null
  routeInvalid?: boolean | null
  manualOverride?: boolean | null
  /** P2-5: 车辆宽度（厘米），用于道路宽度可用性检查 */
  widthCm?: number
  /** P2-5: 车辆长度（厘米） */
  lengthCm?: number
  /** P2-5: 最小转弯半径（米），用于窄路/急弯过滤 */
  turningRadiusM?: number
  /** P2-5: 允许道路等级（逗号分隔，NULL=全部；如 ARTERIAL,SECONDARY） */
  allowedRoadClasses?: string
}

export interface ParkGeofence {
  id: number
  parkId: number
  fenceCode: string
  fenceName: string
  fenceType: 'BOUNDARY' | 'RESTRICTED' | string
  scopeCode?: 'L1_CORE' | 'L1_CANDIDATE_ENVELOPE' | 'SAFETY_RESTRICTED' | string
  dispatchable?: boolean
  polygon: [number, number][]
  status: string
  remark?: string
  updatedAt?: string | null
}

export interface ParkOverviewItem {
  parkId: number
  parkCode: string
  parkName: string
  centerLng?: number | null
  centerLat?: number | null
  mapProvider?: string | null
  vehicleCount: number
  onlineCount: number
  busyCount: number
}

export interface ParkOrderSnapshot {
  orderId: number
  orderNo: string
  orderStatus: string
  taskId: number | null
  taskNo: string | null
  taskStatus: string | null
  vehicleId: number | null
  vehicleCode: string | null
  vehicleName: string | null
  runtimeStage: string
  pickupStation: ParkStation
  dropoffStation: ParkStation
  deliveryZone?: 'GEO_DELIVERY' | 'SCHEMATIC'
  weight?: number | null
  estimatedArrivalTime?: string | null
  assignTime: string | null
  startTime: string | null
  finishTime: string | null
  updatedAt: string | null
}

export interface ParkOrderCreateRequest {
  parkId?: number
  routeId?: number
  externalOrderNo?: string
  pickupStationId: number
  dropoffStationId: number
  priority?: string
  orderPriority?: 'HIGH' | 'NORMAL' | 'LOW'
  deliveryZone?: 'GEO_DELIVERY' | 'SCHEMATIC'
  weight?: number
  remark?: string
}

export interface ParkOrderCreateResponse {
  orderId: number
  orderNo: string
  orderStatus: string
  taskId: number | null
  taskNo: string | null
  taskStatus: string | null
  vehicleId: number | null
  message: string
}
