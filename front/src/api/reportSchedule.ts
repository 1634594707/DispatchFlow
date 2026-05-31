import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { AdminReportSchedule } from '@/types/reportSchedule'

export function fetchReportSchedules() {
  return request.get<any, ApiResponse<AdminReportSchedule[]>>('/admin/report-schedules')
}

export function upsertReportSchedule(payload: AdminReportSchedule) {
  return request.post<any, ApiResponse<AdminReportSchedule>>('/admin/report-schedules', payload)
}

export function deleteReportSchedule(id: number) {
  return request.delete<any, ApiResponse<void>>(`/admin/report-schedules/${id}`)
}
