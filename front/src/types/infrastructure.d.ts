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
  status: 'ACTIVE' | 'INACTIVE'
  sortOrder?: number
  capacityLimit?: number
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
