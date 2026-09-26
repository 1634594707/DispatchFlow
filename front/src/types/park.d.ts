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
  /** 站点启停状态（`ACTIVE`/`INACTIVE`）。旧版后端或未 mock 的响应可能不带 ⇒ 判定要容忍 undefined。 */
  status?: string
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
  parkId?: number | null
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
  /** 数据年龄（秒）：当前时间 - 最后遥测时间 */
  telemetryAgeSeconds?: number | null
  /** 服务端统一遥测过期阈值（秒） */
  telemetryStaleThresholdSeconds?: number | null
  runtimeStage: string
  targetCode: string | null
  targetType: string | null
  charging: boolean
  lowBattery: boolean
  linkMode: 'SIM' | 'REAL' | 'VDA5050'
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
  weight?: number | null
  estimatedArrivalTime?: string | null
  assignTime: string | null
  startTime: string | null
  finishTime: string | null
  updatedAt: string | null
}

/**
 * `GET /admin/park/track` 的"最近几单"精简行（路线图 §16.3）。
 *
 * 刻意不含站点对象与折线：移动页的订单切换只用 orderNo，"这单还在不在跑"只用 runtimeStage；
 * 把整园订单连同每台车三条折线一起搬回来，是原来每次轮询 209 KB 的主要来源。
 */
export interface ParkOrderTrackRow {
  orderId: number
  orderNo: string
  orderStatus: string
  /** 由订单与任务推出的阶段，**不带**车队实时阶段（见后端 toRecentOrder 的注释）。 */
  runtimeStage: string
  vehicleId: number | null
  pickupStationCode: string | null
  pickupStationArea: string | null
  dropoffStationCode: string | null
  dropoffStationArea: string | null
}

export interface ParkTrackResponse {
  /** 正在追踪的这一单；园区内没有任何单时为 null。 */
  order: ParkOrderSnapshot | null
  /** 派给这一单的那台车；未派车或车不可监测时为 null。 */
  vehicle: ParkVehicleSnapshot | null
  recentOrders: ParkOrderTrackRow[]
  /**
   * 该园区还在进行的订单总数（走 park_id + status 索引的 COUNT）。
   * 页头"N 单配送中"用它，不用被 recentLimit 截过的 recentOrders.length。
   */
  activeCount: number | null
}

/** 下单端点：登记站点与"地图上点的任意坐标"二选一，不能同时给。 */export type ParkOrderEndpoint =
  | { kind: 'station'; stationId: number }
  | { kind: 'coord'; lng: number; lat: number }

export interface ParkOrderCreateRequest {
  /** 幂等键：每次下单意图生成一次；重复提交后端返回原订单 */
  idempotencyKey: string
  parkId?: number
  routeId?: number
  externalOrderNo?: string
  pickupStationId?: number
  dropoffStationId?: number
  /** 快递式任意点下单：GCJ-02 坐标。给了它就不给对应的 stationId。 */
  pickupLng?: number
  pickupLat?: number
  dropoffLng?: number
  dropoffLat?: number
  priority?: string
  orderPriority?: 'HIGH' | 'NORMAL' | 'LOW'
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
  /** true：本次响应为幂等重放（重复提交返回原订单） */
  replayed?: boolean
}
