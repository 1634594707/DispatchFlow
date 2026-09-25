import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'

export interface DispatchPauseStatus {
  parkId?: number
  globalPaused: boolean
  parkPaused: boolean
  /** 生效中那次暂停的审计信息；未暂停时后端返回 null。 */
  pauseReason?: string | null
  pausedBy?: string | null
  pausedAt?: string | null
}

export function fetchDispatchPauseStatus(parkId?: number) {
  return request.get<any, ApiResponse<DispatchPauseStatus>>('/admin/dispatch/pause', {
    params: parkId != null ? { parkId } : undefined,
  })
}

/**
 * 暂停必须带原因（后端 `DISPATCH_PAUSE_REASON_REQUIRED` 会拒），恢复可空。
 * 操作人不从这里传 —— 由服务端按会话写入，客户端自报的身份不作数。
 */
export function setDispatchPause(parkId: number | null, paused: boolean, reason?: string) {
  return request.post<any, ApiResponse<DispatchPauseStatus>>('/admin/dispatch/pause', {
    parkId,
    paused,
    reason,
  })
}
