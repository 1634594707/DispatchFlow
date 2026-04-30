import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { DashboardSummary } from '@/types/dashboard'
import type { ExceptionAdminListItem } from '@/types/exception'

export function getDashboardSummary() {
  return request.get<any, ApiResponse<DashboardSummary>>('/admin/dashboard/summary')
}

export function getRecentExceptions(params: { pageNo: number; pageSize: number }) {
  return request.get<any, ApiResponse<any>>('/admin/exceptions', { params })
}
