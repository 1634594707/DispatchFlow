import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { TrafficSegment } from '@/types/traffic'

export function fetchTrafficOverview(parkId?: number) {
  return request.get<any, ApiResponse<TrafficSegment[]>>('/admin/traffic/overview', {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function refreshCongestion(parkId?: number) {
  return request.post<any, ApiResponse<void>>('/admin/traffic/refresh-congestion', null, {
    params: parkId != null ? { parkId } : undefined,
  })
}
