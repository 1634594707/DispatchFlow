import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { DashboardSummary } from '@/types/dashboard'

export function getDashboardSummary(parkId?: number) {
  return request.get<any, ApiResponse<DashboardSummary>>('/admin/dashboard/summary', {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function getRecentExceptions(params: { pageNo: number; pageSize: number }) {
  return request.get<any, ApiResponse<any>>('/admin/exceptions', { params })
}
