export interface AnalyticsTrendPoint {
  label: string
  totalCount: number
  completedCount: number
  completionRate: number
}

export interface AnalyticsHourlyPoint {
  hour: number
  orderCount: number
  taskCount: number
}

export interface AnalyticsEfficiency {
  period: string
  orderCompletionTrend: AnalyticsTrendPoint[]
  avgTaskDurationMinutes: number
  vehicleUtilizationRate: number
  peakHours: AnalyticsHourlyPoint[]
}

export interface AnalyticsTypeCount {
  type: string
  count: number
  ratio: number
}

export interface AnalyticsExceptionAnalysis {
  period: string
  typeDistribution: AnalyticsTypeCount[]
  exceptionTrend: AnalyticsTrendPoint[]
  avgResolutionMinutes: number
  rootCauseHints: AnalyticsTypeCount[]
}

export interface AnalyticsDailySummary {
  date: string
  orderTotal: number
  orderCompleted: number
  orderCompletionRate: number
  taskTotal: number
  taskSuccess: number
  openExceptionCount: number
  resolvedExceptionCount: number
  dayOverDayOrderRate: number
  weekOverWeekOrderRate: number
  highlightEvents: string[]
}

export interface AnalyticsChargingSession {
  sessionId: number
  vehicleId: number
  vehicleCode: string
  chargingPileId: number
  pileCode: string
  startSoc: number
  currentSoc: number
  startTime: string
  elapsedMinutes: number
}

export interface AnalyticsChargingHistory {
  sessionId: number
  vehicleId: number
  vehicleCode: string
  pileCode: string
  startSoc: number
  endSoc: number
  startTime: string
  endTime: string
  durationMinutes: number
  chargeSpeedPerHour: number | null
}

export interface AnalyticsParkCompareItem {
  parkId: number
  parkName: string
  orderCount: number
  taskSuccessCount: number
  openExceptionCount: number
}

export interface AnalyticsChargingOverview {
  activeSessions: AnalyticsChargingSession[]
  activeSessionCount: number
  occupiedPileCount: number
  totalPileCount: number
  avgChargeSpeedPerHour: number
  recentHistory: AnalyticsChargingHistory[]
}
