import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface DispatchPauseStatus {
  parkId?: number
  globalPaused: boolean
  parkPaused: boolean
}

export function fetchDispatchPauseStatus(parkId?: number) {
  return request.get<any, ApiResponse<DispatchPauseStatus>>('/admin/dispatch/pause', {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function setDispatchPause(parkId: number | null, paused: boolean) {
  return request.post<any, ApiResponse<DispatchPauseStatus>>('/admin/dispatch/pause', {
    parkId,
    paused,
  })
}
