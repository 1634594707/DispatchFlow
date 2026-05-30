import request from '@/utils/request'
import type { ApiResponse, PageResponse } from '@/types/api'
import type { TaskQueryRequest, TaskAdminListItem, TaskDetailResponse } from '@/types/task'

export interface TaskAssignResponse {
  taskId: number
  status: string
  vehicleId?: number | null
  message?: string
  assignExplanation?: string | null
  failReasonCode?: string | null
  reasonCode?: string | null
  reasonMessage?: string | null
  suggestions?: string[] | null
  selectedVehicleCode?: string | null
  assignScore?: number | null
}

export interface TaskManualAssignRequest {
  vehicleId: number
  remark?: string
}

export function getTaskList() {
  return request.get<any, ApiResponse<TaskAdminListItem[]>>('/admin/tasks')
}

export function queryTasks(data: TaskQueryRequest) {
  return request.post<any, ApiResponse<PageResponse<TaskAdminListItem>>>('/admin/tasks/query', data)
}

export function getTaskDetail(taskId: number) {
  return request.get<any, ApiResponse<TaskDetailResponse>>(`/admin/tasks/${taskId}`)
}

export function autoAssignTask(taskId: number) {
  return request.post<any, ApiResponse<TaskAssignResponse>>(`/admin/tasks/${taskId}/auto-assign`)
}

export function manualAssignTask(taskId: number, data: TaskManualAssignRequest) {
  return request.post<any, ApiResponse<TaskAssignResponse>>(`/admin/tasks/${taskId}/manual-assign`, data)
}

export function cancelTask(taskId: number, remark?: string) {
  return request.post<any, ApiResponse<TaskAssignResponse>>(`/admin/tasks/${taskId}/cancel`, { remark })
}

export function reassignTask(taskId: number, data: TaskManualAssignRequest) {
  return request.post<any, ApiResponse<TaskAssignResponse>>(`/admin/tasks/${taskId}/reassign`, data)
}

export function batchAutoAssign(taskIds: number[]) {
  return request.post<any, ApiResponse<import('@/types/operateLog').BatchTaskResult>>('/admin/tasks/batch/auto-assign', { taskIds })
}

export function batchCancelTasks(taskIds: number[], remark?: string) {
  return request.post<any, ApiResponse<import('@/types/operateLog').BatchTaskResult>>('/admin/tasks/batch/cancel', { taskIds, remark })
}

export function batchReassignTasks(taskIds: number[], vehicleId: number, remark?: string) {
  return request.post<any, ApiResponse<import('@/types/operateLog').BatchTaskResult>>('/admin/tasks/batch/reassign', { taskIds, vehicleId, remark })
}

export function bumpTaskPriority(taskId: number) {
  return request.post<any, ApiResponse<null>>(`/admin/tasks/${taskId}/bump-priority`)
}
