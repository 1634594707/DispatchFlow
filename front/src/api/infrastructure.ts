import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type {
  AdminChargingPile,
  AdminChargingPileUpsertPayload,
  AdminPark,
  AdminParkUpsertPayload,
  AdminParkingSlot,
  AdminParkingSlotUpsertPayload,
  AdminRoadNode,
  AdminRoadNodeUpsertPayload,
  AdminRoadSegment,
  AdminRoadSegmentUpsertPayload,
  AdminStation,
  AdminStationUpsertPayload,
} from '@/types/infrastructure'

export function fetchParks() {
  return request.get<any, ApiResponse<AdminPark[]>>('/admin/infrastructure/parks')
}

export function createPark(payload: AdminParkUpsertPayload) {
  return request.post<any, ApiResponse<AdminPark>>('/admin/infrastructure/parks', payload)
}

export function updatePark(parkId: number, payload: AdminParkUpsertPayload) {
  return request.put<any, ApiResponse<AdminPark>>(`/admin/infrastructure/parks/${parkId}`, payload)
}

export function toggleParkStatus(parkId: number) {
  return request.post<any, ApiResponse<AdminPark>>(`/admin/infrastructure/parks/${parkId}/toggle-status`)
}

export function fetchStations(parkId?: number) {
  return request.get<any, ApiResponse<AdminStation[]>>('/admin/infrastructure/stations', {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function createStation(payload: AdminStationUpsertPayload) {
  return request.post<any, ApiResponse<AdminStation>>('/admin/infrastructure/stations', payload)
}

export function updateStation(stationId: number, payload: AdminStationUpsertPayload) {
  return request.put<any, ApiResponse<AdminStation>>(`/admin/infrastructure/stations/${stationId}`, payload)
}

export function fetchParkingSlots(parkId?: number) {
  return request.get<any, ApiResponse<AdminParkingSlot[]>>('/admin/infrastructure/parking-slots', {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function createParkingSlot(payload: AdminParkingSlotUpsertPayload) {
  return request.post<any, ApiResponse<AdminParkingSlot>>('/admin/infrastructure/parking-slots', payload)
}

export function updateParkingSlot(slotId: number, payload: AdminParkingSlotUpsertPayload) {
  return request.put<any, ApiResponse<AdminParkingSlot>>(`/admin/infrastructure/parking-slots/${slotId}`, payload)
}

export function fetchChargingPiles(parkId?: number) {
  return request.get<any, ApiResponse<AdminChargingPile[]>>('/admin/infrastructure/charging-piles', {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function createChargingPile(payload: AdminChargingPileUpsertPayload) {
  return request.post<any, ApiResponse<AdminChargingPile>>('/admin/infrastructure/charging-piles', payload)
}

export function updateChargingPile(pileId: number, payload: AdminChargingPileUpsertPayload) {
  return request.put<any, ApiResponse<AdminChargingPile>>(`/admin/infrastructure/charging-piles/${pileId}`, payload)
}

export interface AdminBatterySwapCabinet {
  id: number
  parkId: number
  parkName?: string
  cabinetCode: string
  cabinetName: string
  coordX: number
  coordY: number
  slotCount?: number
  status: string
  remark?: string
}

export interface AdminBatterySwapCabinetUpsertPayload {
  parkId: number
  cabinetCode: string
  cabinetName: string
  coordX: number
  coordY: number
  slotCount?: number
  status?: string
  remark?: string
}

export function fetchSwapCabinets(parkId?: number) {
  return request.get<any, ApiResponse<AdminBatterySwapCabinet[]>>('/admin/infrastructure/swap-cabinets', {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function createSwapCabinet(payload: AdminBatterySwapCabinetUpsertPayload) {
  return request.post<any, ApiResponse<AdminBatterySwapCabinet>>('/admin/infrastructure/swap-cabinets', payload)
}

export function updateSwapCabinet(cabinetId: number, payload: AdminBatterySwapCabinetUpsertPayload) {
  return request.put<any, ApiResponse<AdminBatterySwapCabinet>>(`/admin/infrastructure/swap-cabinets/${cabinetId}`, payload)
}

export function deleteSwapCabinet(cabinetId: number) {
  return request.post<any, ApiResponse<void>>(`/admin/infrastructure/swap-cabinets/${cabinetId}/delete`)
}

export function fetchRoadNodes(parkId?: number) {
  return request.get<any, ApiResponse<AdminRoadNode[]>>('/admin/infrastructure/road-nodes', {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function createRoadNode(payload: AdminRoadNodeUpsertPayload) {
  return request.post<any, ApiResponse<AdminRoadNode>>('/admin/infrastructure/road-nodes', payload)
}

export function updateRoadNode(nodeId: number, payload: AdminRoadNodeUpsertPayload) {
  return request.put<any, ApiResponse<AdminRoadNode>>(`/admin/infrastructure/road-nodes/${nodeId}`, payload)
}

export function fetchRoadSegments(parkId?: number) {
  return request.get<any, ApiResponse<AdminRoadSegment[]>>('/admin/infrastructure/road-segments', {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function createRoadSegment(payload: AdminRoadSegmentUpsertPayload) {
  return request.post<any, ApiResponse<AdminRoadSegment>>('/admin/infrastructure/road-segments', payload)
}

export function updateRoadSegment(segmentId: number, payload: AdminRoadSegmentUpsertPayload) {
  return request.put<any, ApiResponse<AdminRoadSegment>>(`/admin/infrastructure/road-segments/${segmentId}`, payload)
}

export function toggleRoadSegmentStatus(segmentId: number) {
  return request.post<any, ApiResponse<AdminRoadSegment>>(`/admin/infrastructure/road-segments/${segmentId}/toggle-status`)
}
