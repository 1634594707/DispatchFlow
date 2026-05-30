export interface OperateLogItem {
  id: number
  taskId: number
  taskNo?: string
  vehicleId?: number
  operateType: string
  beforeStatus?: string
  afterStatus?: string
  operatorType: string
  operatorId?: string
  operatorName?: string
  operateRemark?: string
  createdAt: string
}

export interface OperateLogQueryRequest {
  taskId?: number
  vehicleId?: number
  operateType?: string
  operatorName?: string
  startTime?: string
  endTime?: string
  pageNo?: number
  pageSize?: number
}

export interface BatchTaskRequest {
  taskIds: number[]
  vehicleId?: number
  remark?: string
}

export interface BatchTaskItemResult {
  taskId: number
  taskNo?: string
  success: boolean
  message?: string
  status?: string
  vehicleId?: number
  reasonCode?: string
  reasonMessage?: string
  suggestions?: string[]
}

export interface BatchTaskResult {
  total: number
  successCount: number
  failureCount: number
  results: BatchTaskItemResult[]
}
