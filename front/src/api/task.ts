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

export function getTaskList(parkId?: number) {
  return request.get<any, ApiResponse<TaskAdminListItem[]>>('/admin/tasks', { params: { parkId } })
}

export function queryTasks(data: TaskQueryRequest) {
  return request.post<any, ApiResponse<PageResponse<TaskAdminListItem>>>('/admin/tasks/query', data)
}

export function getTaskDetail(taskId: number, parkId?: number) {
  return request.get<any, ApiResponse<TaskDetailResponse>>(`/admin/tasks/${taskId}`, {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function autoAssignTask(taskId: number, parkId?: number) {
  return request.post<any, ApiResponse<TaskAssignResponse>>(`/admin/tasks/${taskId}/auto-assign`, undefined, {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function manualAssignTask(taskId: number, data: TaskManualAssignRequest, parkId?: number) {
  return request.post<any, ApiResponse<TaskAssignResponse>>(`/admin/tasks/${taskId}/manual-assign`, data, {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function cancelTask(taskId: number, remark?: string, parkId?: number) {
  return request.post<any, ApiResponse<TaskAssignResponse>>(`/admin/tasks/${taskId}/cancel`, { remark }, {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function reassignTask(taskId: number, data: TaskManualAssignRequest, parkId?: number) {
  return request.post<any, ApiResponse<TaskAssignResponse>>(`/admin/tasks/${taskId}/reassign`, data, {
    params: parkId != null ? { parkId } : undefined,
  })
}

export function batchAutoAssign(taskIds: number[], parkId?: number) {
  return request.post<any, ApiResponse<import('@/types/operateLog').BatchTaskResult>>('/admin/tasks/batch/auto-assign', { taskIds, parkId })
}

export function batchCancelTasks(taskIds: number[], remark?: string, parkId?: number) {
  return request.post<any, ApiResponse<import('@/types/operateLog').BatchTaskResult>>('/admin/tasks/batch/cancel', { taskIds, remark, parkId })
}

export function batchReassignTasks(taskIds: number[], vehicleId: number, remark?: string, parkId?: number) {
  return request.post<any, ApiResponse<import('@/types/operateLog').BatchTaskResult>>('/admin/tasks/batch/reassign', { taskIds, vehicleId, remark, parkId })
}

export function batchUnassignTasks(taskIds: number[], remark?: string, parkId?: number) {
  return request.post<any, ApiResponse<import('@/types/operateLog').BatchTaskResult>>('/admin/tasks/batch/unassign', { taskIds, remark, parkId })
}

export function bumpTaskPriority(taskId: number, parkId?: number) {
  return request.post<any, ApiResponse<null>>(`/admin/tasks/${taskId}/bump-priority`, undefined, {
    params: parkId != null ? { parkId } : undefined,
  })
}
