import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { AdminChargingPile } from '@/types/infrastructure'

/** 充电站信息 */
export interface ChargingStation {
  id: number
  parkId: number
  parkName?: string
  stationCode: string
  stationName: string
  /** 空闲桩数 */
  freePileCount: number
  /** 总桩数 */
  totalPileCount: number
  /** 快充桩数 */
  fastPileCount: number
  /** 慢充桩数 */
  slowPileCount: number
  coordX?: number
  coordY?: number
  longitude?: number
  latitude?: number
}

/** 充电桩占用情况（Redis） */
export interface ChargingStationOccupancy {
  stationId: number
  stationCode: string
  totalPiles: number
  occupiedPiles: number
  freePiles: number
  /** 各桩位状态详情 */
  pileStatuses: { pileId: number; pileCode: string; status: string; vehicleId?: number }[]
}

/** 回充路径 */
export interface ReturnToChargeRoute {
  vehicleId: number
  vehicleCode: string
  chargingStationId: number
  chargingStationCode: string
  /** 预估路径距离 / 段数 */
  routeDistance?: number
  routeDurationSeconds?: number
  /** 路径坐标点（地理或园区坐标） */
  routePoints?: { x: number; y: number }[]
  /** 到达后预计可用的充电桩 ID */
  recommendedPileId?: number
}

/** 获取所有充电站（含占用统计） */
export function fetchChargingStations(parkId?: number) {
  return request.get<any, ApiResponse<ChargingStation[]>>('/admin/charging/stations', {
    params: parkId != null ? { parkId } : undefined,
  })
}

/** 获取回充路径 */
export function fetchReturnToChargeRoute(vehicleId: number, stationId: number) {
  return request.get<any, ApiResponse<ReturnToChargeRoute>>(
    `/admin/charging/return-to-charge/route`,
    { params: { vehicleId, stationId } }
  )
}

/** 获取充电站桩位占用情况（Redis 实时） */
export function fetchChargingStationOccupancy(stationId: number) {
  return request.get<any, ApiResponse<ChargingStationOccupancy>>(
    `/admin/charging/stations/${stationId}/occupancy`
  )
}

/** 获取所有充电站桩位占用情况 */
export function fetchAllChargingStationOccupancy() {
  return request.get<any, ApiResponse<ChargingStationOccupancy[]>>(
    '/admin/charging/stations/occupancy'
  )
}

/** 获取充电桩列表（桩位级别） */
export function fetchChargingPiles(parkId?: number) {
  return request.get<any, ApiResponse<AdminChargingPile[]>>('/admin/infrastructure/charging-piles', {
    params: parkId != null ? { parkId } : undefined,
  })
}