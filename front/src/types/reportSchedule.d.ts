export interface AdminReportSchedule {
  id?: number
  parkId?: number | null
  cronExpression: string
  recipients: string
  enabled?: boolean
  lastSentAt?: string
}
