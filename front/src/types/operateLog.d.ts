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

/** V5-S1: 配置审计日志条目 */
export interface ConfigAuditLogItem {
  id: number
  configKey: string
  beforeValue?: string
  afterValue?: string
  changeReason?: string
  operateType: 'DISPATCH_STRATEGY_CHANGE' | 'TRAFFIC_CONTROL_CHANGE' | 'STATION_CONFIG_CHANGE' | 'ALERT_SETTING_CHANGE'
  operatorName?: string
  operatorId?: string
  createdAt: string
}

/** V5-S1: 配置审计查询请求 */
export interface ConfigAuditQueryRequest {
  configKey?: string
  changeReason?: string
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
