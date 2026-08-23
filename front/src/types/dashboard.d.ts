export interface DashboardSummary {
  parkId?: number | null
  pendingCount: number
  assigningCount: number
  manualPendingCount: number
  executingCount: number
  failedCount: number
  onlineVehicleCount: number
  idleVehicleCount: number
  busyVehicleCount: number
  openExceptionCount?: number
}
