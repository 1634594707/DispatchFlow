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
    localStorage.getItem('fsd_mobile_api_key')?.trim() ||
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
