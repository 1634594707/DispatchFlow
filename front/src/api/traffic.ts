import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { TrafficPauseZone, TrafficSegment, TrafficSummary } from '@/types/traffic'

export function fetchTrafficOverview(parkId?: number) {
  return request.get<any, ApiResponse<TrafficSegment[]>>('/admin/traffic/overview', {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function fetchTrafficSummary(parkId?: number) {
  return request.get<any, ApiResponse<TrafficSummary>>('/admin/traffic/summary', {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function refreshCongestion(parkId?: number) {
  return request.post<any, ApiResponse<void>>('/admin/traffic/refresh-congestion', null, {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function disableTrafficSegment(segmentId: number) {
  return request.post<any, ApiResponse<TrafficSegment>>(`/admin/traffic/segments/${segmentId}/disable`)
}

export function downgradeTrafficSegment(segmentId: number) {
  return request.post<any, ApiResponse<TrafficSegment>>(`/admin/traffic/segments/${segmentId}/downgrade-congestion`)
}

export function listTrafficPauseZones(parkId?: number) {
  return request.get<any, ApiResponse<TrafficPauseZone[]>>('/admin/traffic/pause-zones', {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function addTrafficPauseZone(payload: {
  parkId: number
  minX: number
  minY: number
  maxX: number
  maxY: number
  label?: string
}) {
  return request.post<any, ApiResponse<TrafficPauseZone>>('/admin/traffic/pause-zones', payload)
}

export function clearTrafficPauseZones(parkId?: number) {
  return request.delete<any, ApiResponse<void>>('/admin/traffic/pause-zones', {
    params: parkId != null ? { parkId } : undefined,
  })
}
