export interface ParkPoint {
  code: string
  x: number
  y: number
}

export interface ParkStation {
  stationId: number
  stationCode: string
  stationName: string
  x: number
  y: number
  area: string
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
  width: number
  height: number
  minZoom: number
  maxZoom: number
  vehicleSpeedPxPerSecond: number
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
  x: number
  y: number
  runtimeStage: string
  targetCode: string | null
  targetType: string | null
  charging: boolean
  lowBattery: boolean
  trajectory: ParkPoint[]
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
  assignTime: string | null
  startTime: string | null
  finishTime: string | null
  updatedAt: string | null
}

export interface ParkOrderCreateRequest {
  externalOrderNo?: string
  pickupStationId: number
  dropoffStationId: number
  priority?: string
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
