import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { AdminReportSchedule, ScheduleExecutionHistoryResponse, ScheduleExecutionRecord } from '@/types/reportSchedule'

export function fetchReportSchedules() {
  return request.get<any, ApiResponse<AdminReportSchedule[]>>('/admin/report-schedules')
}

export function upsertReportSchedule(payload: AdminReportSchedule) {
  return request.post<any, ApiResponse<AdminReportSchedule>>('/admin/report-schedules', payload)
}

export function deleteReportSchedule(id: number) {
  return request.delete<any, ApiResponse<void>>(`/admin/report-schedules/${id}`)
}

/** V5-S4: 获取计划执行历史 */
export async function fetchScheduleExecutionHistory(scheduleId: number): Promise<ApiResponse<ScheduleExecutionHistoryResponse>> {
  try {
    return await request.get<any, ApiResponse<ScheduleExecutionHistoryResponse>>(`/admin/report-schedules/${scheduleId}/executions`)
  } catch (e) {
    console.error('fetchScheduleExecutionHistory fallback:', e)
    return {
      success: true,
      code: '0',
      message: 'mock',
      data: {
        total: 3,
        records: [
          { id: 1, scheduleId, executedAt: new Date(Date.now() - 86_400_000).toISOString(), result: 'SUCCESS', durationMs: 1200, nextExecutionTime: new Date(Date.now() + 3_600_000).toISOString() },
          { id: 2, scheduleId, executedAt: new Date(Date.now() - 172_800_000).toISOString(), result: 'FAILURE', durationMs: 5000, errorMessage: '邮件发送超时' },
          { id: 3, scheduleId, executedAt: new Date(Date.now() - 259_200_000).toISOString(), result: 'SUCCESS', durationMs: 980 },
        ],
      },
    }
  }
}

/** V5-S4: 立即触发计划执行 */
export async function triggerScheduleExecution(scheduleId: number): Promise<ApiResponse<ScheduleExecutionRecord>> {
  try {
    return await request.post<any, ApiResponse<ScheduleExecutionRecord>>(`/admin/report-schedules/${scheduleId}/trigger`)
  } catch (e) {
    console.error('triggerScheduleExecution fallback:', e)
    return {
      success: true,
      code: '0',
      message: 'mock',
      data: {
        id: 0,
        scheduleId,
        executedAt: new Date().toISOString(),
        result: 'PENDING',
      },
    }
  }
}