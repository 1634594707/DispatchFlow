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

function resolveMobileApiKey(): string | undefined {
  return (
    sessionStorage.getItem('fsd_mobile_api_key')?.trim() ||
    (import.meta.env.VITE_MOBILE_API_KEY as string | undefined)?.trim() ||
    undefined
  )
}

function mobileApiHeaders(): Record<string, string> | undefined {
  const key = resolveMobileApiKey()
  return key ? { 'X-Mobile-Api-Key': key } : undefined
}

export function listParks() {
  return request.get<any, ApiResponse<ParkSummary[]>>('/admin/parks', {
    headers: mobileApiHeaders(),
  })
}

export function getParkLayout(parkId?: number) {
  return request.get<any, ApiResponse<ParkLayout>>('/admin/park/layout', {
    params: parkId != null ? { parkId } : undefined,
    headers: mobileApiHeaders(),
  })
}

export function getParkStations(parkId?: number) {
  return request.get<any, ApiResponse<ParkStation[]>>('/admin/park/stations', {
    params: parkId != null ? { parkId } : undefined,
    headers: mobileApiHeaders(),
  })
}

export function getParkVehicles(options?: { silent?: boolean }) {
  return request.get<any, ApiResponse<ParkVehicleSnapshot[]>>('/admin/park/vehicles', {
    headers: mobileApiHeaders(),
    skipErrorToast: options?.silent,
  })
}

export function getParkGeofences(parkId?: number) {
  return request.get<any, ApiResponse<import('@/types/park').ParkGeofence[]>>('/admin/park/geofences', {
    params: parkId != null ? { parkId } : undefined,
    headers: mobileApiHeaders(),
  })
}

export function getParkOverview() {
  return request.get<any, ApiResponse<import('@/types/park').ParkOverviewItem[]>>('/admin/park/overview')
}

export function getParkOrders(options?: { silent?: boolean }) {
  return request.get<any, ApiResponse<ParkOrderSnapshot[]>>('/admin/park/orders', {
    headers: mobileApiHeaders(),
    skipErrorToast: options?.silent,
  })
}

export interface RoadRouteHealth {
  amapDriving: boolean
  localGraph: boolean
  amapSuccessCount: number
  fallbackCount: number
  localGraphSegments: number
  detail?: string
}

export interface RoadRouteValidateResult {
  invalid: boolean
  crossesBuilding: boolean
  crossesRiver: boolean
  nearestRoadDistanceMeters: number
  vertexCount: number
  source?: string | null
}

export function getRoadRouteHealth() {
  return request.get<any, ApiResponse<RoadRouteHealth>>('/admin/park/road-route/health')
}

export function validateRoadRoute(data: {
  originLng: number
  originLat: number
  destinationLng: number
  destinationLat: number
  polyline?: Array<{ longitude: number; latitude: number }>
}) {
  return request.post<any, ApiResponse<RoadRouteValidateResult>>('/admin/park/road-route/validate', data)
}

export function createParkOrder(data: ParkOrderCreateRequest, mobileApiKey?: string) {
  const key = mobileApiKey?.trim() || resolveMobileApiKey()
  return request.post<any, ApiResponse<ParkOrderCreateResponse>>('/admin/park/orders', data, {
    headers: key ? { 'X-Mobile-Api-Key': key } : undefined,
  })
}

/** Phase 5 任务 5.3：GCJ-02 ↔ 园区 schematic x/y 坐标互转。 */
export interface GeoTransformResult {
  parkX: number
  parkY: number
  longitude: number
  latitude: number
}

/**
 * 阶段七 7.3：园区元数据（替代硬编码 ZJF_PILOT_GEO）。
 * 后端 GET /api/admin/park/metadata 返回园区锚点/尺寸/场景标识等元数据。
 */
export interface ParkMetadata {
  parkId: number
  parkCode: string
  parkName: string
  scenarioCode?: string | null
  anchorLng: number | null
  anchorLat: number | null
  centerLng: number | null
  centerLat: number | null
  parkWidthPx: number | null
  parkHeightPx: number | null
  parkWidthMeters: number | null
  parkHeightMeters: number | null
  mapProvider?: string | null
}

export function getParkMetadata(parkId?: number) {
  return request.get<any, ApiResponse<ParkMetadata>>('/admin/park/metadata', {
    params: parkId != null ? { parkId } : undefined,
    headers: mobileApiHeaders(),
  })
}

export function transformGeoCoordinates(params: {
  parkX?: number
  parkY?: number
  longitude?: number
  latitude?: number
}) {
  return request.get<any, ApiResponse<GeoTransformResult>>('/admin/park/geo/transform', {
    params,
    headers: mobileApiHeaders(),
  })
}
