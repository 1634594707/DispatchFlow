import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { SystemHealthResponse } from '@/types/phase10'

export function getSystemHealth() {
  return request.get<any, ApiResponse<SystemHealthResponse>>('/admin/system/health')
}
