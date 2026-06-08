import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type {
  DigitalTwinComparisonResult,
  DigitalTwinSimulateResult,
  DigitalTwinSnapshot,
} from '@/types/phase10'

export function getDigitalTwinSnapshot(parkId?: number | null) {
  const params: Record<string, number> = {}
  if (parkId != null && Number.isFinite(parkId) && parkId > 0) {
    params.parkId = parkId
  }
  return request.get<any, ApiResponse<DigitalTwinSnapshot>>('/admin/digital-twin/snapshot', { params })
}

export function simulateDigitalTwin(data: {
  parkId?: number | null
  scenario: string
  pendingTaskCount?: number
  speed?: number
  orderInterval?: number
  vehicleCount?: number
}) {
  return request.post<any, ApiResponse<DigitalTwinSimulateResult>>('/admin/digital-twin/simulate', data)
}

export function compareSimulateDigitalTwin(data: {
  parkId?: number | null
  scenarioA: string
  paramsA: { speed: number; orderInterval: number; vehicleCount: number }
  scenarioB: string
  paramsB: { speed: number; orderInterval: number; vehicleCount: number }
  pendingTaskCount?: number
}) {
  return request.post<any, ApiResponse<DigitalTwinComparisonResult>>(
    '/admin/digital-twin/compare',
    data,
  )
}