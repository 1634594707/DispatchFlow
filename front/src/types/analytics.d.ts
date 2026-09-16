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
  activeSwapSessionCount?: number
  totalChargeDurationMinutes?: number
  totalSwapDurationMinutes?: number
  recentHistory: AnalyticsChargingHistory[]
}

/** ALG-FC 补能需求预测：某站某小时的需求点（来源 t_energy_forecast）。 */
export interface AnalyticsEnergyForecastHourPoint {
  hourOfDay: number
  demandP50: number
  demandP90: number
  pressureP95: number
}

/** 单站 24 小时剖面。stale=true 表示最新一行已超期，派单侧已回退纯阈值策略。 */
export interface AnalyticsEnergyForecastStation {
  parkId: number | null
  stationId: number
  stationCode: string | null
  modelVersion: string | null
  generatedAt: string | null
  stale: boolean
  sampleCount: number
  /** 实际小时数，正常 24；缺小时不补齐。 */
  hourCount: number
  peakPressureP95: number
  peakHourOfDay: number
  peakDemandP90: number
  pressureThresholdExceeded: boolean
  hours: AnalyticsEnergyForecastHourPoint[]
}

export interface AnalyticsEnergyForecast {
  enabled: boolean
  pressureThreshold: number
  maxDataAgeHours: number
  forecastDate: string
  parkId: number | null
  /** 接口响应时刻，用于在图上定位"当前小时"。 */
  serverTime: string
  stationCount: number
  anyData: boolean
  anyStale: boolean
  stations: AnalyticsEnergyForecastStation[]
}
