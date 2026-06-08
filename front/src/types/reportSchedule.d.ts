export interface AdminReportSchedule {
  id?: number
  parkId?: number | null
  cronExpression: string
  recipients: string
  enabled?: boolean
  lastSentAt?: string
}

/** V5-S4: 计划执行历史条目 */
export interface ScheduleExecutionRecord {
  id: number
  scheduleId: number
  executedAt: string
  result: 'SUCCESS' | 'FAILURE' | 'PENDING'
  durationMs?: number
  errorMessage?: string
  nextExecutionTime?: string
}

/** V5-S4: 执行历史查询响应 */
export interface ScheduleExecutionHistoryResponse {
  records: ScheduleExecutionRecord[]
  total: number
}

/** V5-S4: 执行状态统计 */
export interface ScheduleExecutionStats {
  lastExecutedAt?: string
  lastResult?: 'SUCCESS' | 'FAILURE' | 'PENDING'
  nextExecutionTime?: string
  totalRuns: number
  successCount: number
  failureCount: number
}