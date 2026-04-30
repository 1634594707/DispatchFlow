import request from '@/utils/request'
import type { ApiResponse, PageResponse } from '@/types/api'
import type { TaskQueryRequest, TaskAdminListItem, TaskDetailResponse } from '@/types/task'

export function getTaskList() {
  return request.get<any, ApiResponse<TaskAdminListItem[]>>('/admin/tasks')
}

export function queryTasks(data: TaskQueryRequest) {
  return request.post<any, ApiResponse<PageResponse<TaskAdminListItem>>>('/admin/tasks/query', data)
}

export function getTaskDetail(taskId: number) {
  return request.get<any, ApiResponse<TaskDetailResponse>>(`/admin/tasks/${taskId}`)
}
