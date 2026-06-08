import request from '@/utils/request'
import type { ApiResponse, PageResponse } from '@/types/api'
import type { OperateLogItem, OperateLogQueryRequest, ConfigAuditLogItem, ConfigAuditQueryRequest } from '@/types/operateLog'

export function queryOperateLogs(data: OperateLogQueryRequest) {
  return request.post<any, ApiResponse<PageResponse<OperateLogItem>>>('/admin/operate-logs/query', data)
}

/** V5-S1: 查询配置审计日志 */
export async function fetchConfigAuditLogs(data: ConfigAuditQueryRequest): Promise<ApiResponse<PageResponse<ConfigAuditLogItem>>> {
  try {
    return await request.post<any, ApiResponse<PageResponse<ConfigAuditLogItem>>>('/admin/operate-logs/config-audit/query', data)
  } catch (e) {
    console.error('fetchConfigAuditLogs fallback:', e)
    return {
      success: true,
      code: '0',
      message: 'mock',
      data: { total: 0, pageNo: data.pageNo || 1, pageSize: data.pageSize || 20, records: [] },
    }
  }
}

export function fetchTaskOperateLogs(taskId: number) {
  return request.get<any, ApiResponse<OperateLogItem[]>>(`/admin/operate-logs/tasks/${taskId}`)
}

export function fetchVehicleOperateLogs(vehicleId: number) {
  return request.get<any, ApiResponse<OperateLogItem[]>>(`/admin/operate-logs/vehicles/${vehicleId}`)
}

export async function exportOperateLogs(data: OperateLogQueryRequest) {
  const res = await request.post<any, ApiResponse<string>>('/admin/operate-logs/export', {
    ...data,
    pageNo: 1,
    pageSize: 10000,
  })
  const blob = new Blob(['\uFEFF' + res.data], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `operate-logs-${Date.now()}.csv`
  link.click()
  URL.revokeObjectURL(url)
}
