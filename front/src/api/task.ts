import request from '@/utils/request'
import type { ApiResponse, PageResponse } from '@/types/api'
import type { TaskQueryRequest, TaskAdminListItem, TaskDetailResponse, TaskTimelineResponse } from '@/types/task'

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

/** 一次派单决策的候选分项（总分越小越优）。 */
export interface DecisionCandidate {
  rank?: number
  vehicleId?: number | null
  vehicleCode?: string | null
  distance?: number
  socMargin?: number
  pluggedBonus?: number
  idleBonus?: number
  priorityFactor?: number
  forecastPenalty?: number
  total?: number
}

/** 决策快照（§7.3 读侧）：候选漏斗、分项分数、分差与影子对照，全部来自 t_dispatch_decision_snapshot。 */
export interface DecisionExplain {
  snapshotId: number
  taskId?: number | null
  orderId?: number | null
  orderNo?: string | null
  policyId: string
  policyVersion: string
  matchAlgorithm?: string | null
  roadGraphVersion?: string | null
  generatedAt?: string | null
  durationMicros?: number | null
  failReason?: string | null
  remark?: string | null
  funnel?: {
    candidateTotal?: number | null
    freshTelemetry?: number | null
    socEligible?: number | null
    socChainEligible?: number | null
    reachable?: number | null
    evaluated?: number | null
  } | null
  winner?: DecisionCandidate | null
  runnerUpScore?: number | null
  scoreGap?: number | null
  tieCount?: number | null
  candidates: DecisionCandidate[]
  shadow?: {
    policyId: string
    policyVersion?: string | null
    winnerCode?: string | null
    agreed?: boolean | null
    regret?: number | null
  } | null
}

/** 按任务读最近的决策快照（含失败那次），用于"当时为什么选这台车、差多少分"。 */
export function getTaskDecisions(taskId: number, limit = 3) {
  return request.get<any, ApiResponse<DecisionExplain[]>>('/admin/dispatch/decisions', {
    params: { taskId, limit },
  })
}

export function queryTasks(data: TaskQueryRequest) {
  return request.post<any, ApiResponse<PageResponse<TaskAdminListItem>>>('/admin/tasks/query', data)
}

export function getTaskDetail(taskId: number, parkId?: number) {
  return request.get<any, ApiResponse<TaskDetailResponse>>(`/admin/tasks/${taskId}`, {
    params: parkId != null ? { parkId } : undefined,
  })
}

/** 统一任务时间线（路线图 3.2）：订单创建→派车→回报→异常→重试/改派→终态 */
export function getTaskTimeline(taskId: number, parkId?: number) {
  return request.get<any, ApiResponse<TaskTimelineResponse>>(`/admin/tasks/${taskId}/timeline`, {
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
