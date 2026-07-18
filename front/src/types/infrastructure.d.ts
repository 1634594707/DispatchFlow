export interface AdminPark {
  id: number
  parkCode: string
  parkName: string
  mapWidth?: number
  mapHeight?: number
  minZoom?: number
  maxZoom?: number
  vehicleSpeedPxPerSecond?: number
  status: 'ACTIVE' | 'INACTIVE'
  defaultPark: boolean
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface AdminParkUpsertPayload {
  parkCode: string
  parkName: string
  mapWidth?: number
  mapHeight?: number
  minZoom?: number
  maxZoom?: number
  vehicleSpeedPxPerSecond?: number
  status?: string
  defaultPark?: boolean
  remark?: string
}

export interface AdminStation {
  id: number
  parkId: number
  parkName?: string
  stationCode: string
  stationName: string
  stationType: 'PICKUP' | 'DROPOFF' | 'GENERAL' | 'HUB' | 'BUFFER' | 'MOTHERSHIP'
  coordX: number
  coordY: number
  coordLng?: number | null
  coordLat?: number | null
  area?: string
  deliveryZone?: 'GEO_DELIVERY' | 'SCHEMATIC' | 'GENERAL'
  status: 'ACTIVE' | 'INACTIVE'
  sortOrder?: number
  capacityLimit?: number
  /** P2-5: 站点接入的道路节点编码（吸附到 road_node.node_code） */
  anchorNodeCode?: string | null
  /** P2-5: 车辆到站服务方向：FORWARD/REVERSE/BIDIRECTIONAL */
  serviceDirection?: string | null
  /** P2-5: 不可达原因：ROAD_CLOSED/NO_SERVICE_POSITION/VEHICLE_TYPE_NOT_ALLOWED/CAPACITY_FULL/MAINTENANCE/OFFLINE/GATE_CLOSED/OUT_OF_RANGE */
  unreachableReason?: string | null
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface AdminStationUpsertPayload {
  parkId: number
  stationCode: string
  stationName: string
  stationType: string
  coordX: number
  coordY: number
  coordLng?: number | null
  coordLat?: number | null
  area?: string
  deliveryZone?: 'GEO_DELIVERY' | 'SCHEMATIC' | 'GENERAL'
  status?: string
  sortOrder?: number
  capacityLimit?: number
  remark?: string
}

export interface AdminParkingSlot {
  id: number
  parkId: number
  parkName?: string
  slotCode: string
  slotName: string
  slotType: 'STANDBY' | 'CHARGING_ONLY'
  coordX: number
  coordY: number
  status: 'FREE' | 'OCCUPIED' | 'RESERVED' | 'CHARGING' | 'FAULT'
  occupiedVehicleId?: number
  sortOrder?: number
  /** P2-5: 车位朝向：NORTH/SOUTH/EAST/WEST/NE/NW/SE/SW */
  facingDirection?: string | null
  /** P2-5: 进站节点编码 */
  entryNodeCode?: string | null
  /** P2-5: 出站节点编码 */
  exitNodeCode?: string | null
  /** P2-5: 是否阻塞主路（1=是，禁止长时间占用；0=否） */
  blockingMainRoad?: number | null
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface AdminParkingSlotUpsertPayload {
  parkId: number
  slotCode: string
  slotName: string
  slotType?: string
  coordX: number
  coordY: number
  status?: string
  sortOrder?: number
  remark?: string
}

export interface AdminChargingPile {
  id: number
  parkId: number
  parkName?: string
  pileCode: string
  pileName: string
  parkingSlotId: number
  parkingSlotCode?: string
  status: 'FREE' | 'OCCUPIED' | 'RESERVED' | 'CHARGING' | 'FAULT'
  occupiedVehicleId?: number
  maxPowerKw?: number
  sortOrder?: number
  /** P2-5: 进站点（充电区入口道路节点编码） */
  entryNodeCode?: string | null
  /** P2-5: 出站点（充电区出口道路节点编码；与 entry 不同则需单向循环） */
  exitNodeCode?: string | null
  /** P2-5: 充电枪类型：CCS2/GB_T_DC/CHAOJI/AC_GENERIC/WIRELESS */
  plugType?: string | null
  /** P2-5: 预约状态：FREE/RESERVED/CHARGING/FAULT */
  reservationState?: string | null
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface AdminChargingPileUpsertPayload {
  parkId: number
  pileCode: string
  pileName: string
  parkingSlotId: number
  status?: string
  maxPowerKw?: number
  sortOrder?: number
  remark?: string
}

export interface AdminRoadNode {
  id: number
  parkId: number
  parkName?: string
  nodeCode: string
  coordX: number
  coordY: number
  status: 'ACTIVE' | 'DISABLED'
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface AdminRoadNodeUpsertPayload {
  parkId: number
  nodeCode: string
  coordX: number
  coordY: number
  status?: string
  remark?: string
}

export interface AdminRoadSegment {
  id: number
  parkId: number
  parkName?: string
  fromNodeCode: string
  toNodeCode: string
  status: 'ACTIVE' | 'DISABLED'
  speedLimitKmh?: number
  congestionLevel?: number
  /** P2-5: 道路可行驶宽度（米），用于车辆外接矩形碰撞检查 */
  widthMeters?: number | null
  /** P2-5: 道路等级：HIGHWAY/ARTERIAL/SECONDARY/SERVICE_ROAD/PEDESTRIAN/FIRE_LANE */
  roadClass?: string | null
  /** P2-5: 通行语义：DRIVABLE/PEDESTRIAN_ONLY/SERVICE_ONLY/RESTRICTED/BLOCKED/NO_STOP/LOADING_ONLY/CHARGING_ACCESS */
  accessState?: string | null
  /** P2-5: 转向限制：NONE/NO_LEFT/NO_RIGHT/NO_U_TURN/NO_STRAIGHT */
  turnRestriction?: string | null
  /** P2-5: 关联门禁/闸机/消防通道编码（NULL=无门禁） */
  gateCode?: string | null
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface AdminRoadSegmentUpsertPayload {
  parkId: number
  fromNodeCode: string
  toNodeCode: string
  status?: string
  speedLimitKmh?: number
  congestionLevel?: number
  remark?: string
}

export interface AdminGeofence {
  id: number
  parkId: number
  parkName?: string
  fenceCode: string
  fenceName: string
  fenceType: 'BOUNDARY' | 'RESTRICTED' | string
  polygon: [number, number][]
  status: string
  remark?: string
  updatedAt?: string
}

export interface AdminGeofenceUpsertPayload {
  parkId: number
  fenceCode: string
  fenceName: string
  fenceType: string
  polygonJson: string
  status?: string
  remark?: string
}

export interface AdminGeofenceUpdatePayload {
  fenceName?: string
  fenceType?: string
  polygonJson?: string
  status?: string
  remark?: string
}
