import { computed, ref } from 'vue'
import type { PredictiveAlert } from '@/types/alert'

interface SocReading {
  soc: number
  timestamp: number
}

interface SocTrendData {
  readings: SocReading[]
  slope: number
}

const HISTORY_SIZE = 5
const PREDICT_WINDOW_MINUTES = 30
const LOW_SOC_THRESHOLD = 30

/**
 * V5-N3: 预测性告警 — SOC 下降趋势预测
 */
export function usePredictiveAlert() {
  const socTrends = ref<Map<number, SocTrendData>>(new Map())

  /** 线性回归计算斜率 */
  function calculateSlope(readings: SocReading[]): number {
    if (readings.length < 2) return 0
    const n = readings.length
    const sumX = readings.reduce((s, r) => s + r.timestamp, 0)
    const sumY = readings.reduce((s, r) => s + r.soc, 0)
    const sumXY = readings.reduce((s, r) => s + r.timestamp * r.soc, 0)
    const sumX2 = readings.reduce((s, r) => s + r.timestamp * r.timestamp, 0)
    const denominator = n * sumX2 - sumX * sumX
    if (denominator === 0) return 0
    return (n * sumXY - sumX * sumY) / denominator
  }

  /** 追加 SOC 读数 */
  function addSocReading(vehicleId: number, soc: number) {
    const now = Date.now()
    const existing = socTrends.value.get(vehicleId) || { readings: [], slope: 0 }
    existing.readings.push({ soc, timestamp: now })
    if (existing.readings.length > HISTORY_SIZE) {
      existing.readings.shift()
    }
    existing.slope = calculateSlope(existing.readings)
    socTrends.value.set(vehicleId, existing)
  }

  /** 预测到达低电量阈值的分钟数 */
  function predictLowSocMinutes(vehicleId: number, threshold = LOW_SOC_THRESHOLD): number | null {
    const trend = socTrends.value.get(vehicleId)
    if (!trend || trend.readings.length < 2) return null
    const latest = trend.readings[trend.readings.length - 1]
    if (latest.soc <= threshold) return 0
    if (trend.slope >= 0) return Infinity
    // slope is SOC per millisecond, negative
    const msUntilThreshold = (threshold - latest.soc) / trend.slope // will be negative / negative = positive
    if (msUntilThreshold < 0) return Infinity
    return Math.round(msUntilThreshold / 60000)
  }

  /** 预测为低风险的车辆列表 */
  const predictLowSocVehicles = computed<PredictiveAlert[]>(() => {
    const result: PredictiveAlert[] = []
    socTrends.value.forEach((trend, vehicleId) => {
      if (trend.readings.length < 2) return
      const latest = trend.readings[trend.readings.length - 1]
      const minutes = predictLowSocMinutes(vehicleId)
      if (minutes == null || minutes === Infinity || minutes > PREDICT_WINDOW_MINUTES) return
      // trend: slope < -0.00005 = rapid decline (~3% per minute)
      let trendLabel: 'stable' | 'slight_decline' | 'rapid_decline' = 'stable'
      const slopePerMin = trend.slope * 60000
      if (slopePerMin < -3) trendLabel = 'rapid_decline'
      else if (slopePerMin < -0.5) trendLabel = 'slight_decline'

      result.push({
        vehicleId,
        vehicleCode: String(vehicleId),
        currentSoc: latest.soc,
        predictedMinutes: minutes,
        trend: trendLabel,
      })
    })
    return result
  })

  /** 获取车辆的趋势方向 */
  function getVehicleTrend(vehicleId: number): 'stable' | 'slight_decline' | 'rapid_decline' {
    const trend = socTrends.value.get(vehicleId)
    if (!trend || trend.readings.length < 2) return 'stable'
    const slopePerMin = trend.slope * 60000
    if (slopePerMin < -3) return 'rapid_decline'
    if (slopePerMin < -0.5) return 'slight_decline'
    return 'stable'
  }

  return {
    socTrends,
    predictLowSocVehicles,
    addSocReading,
    predictLowSocMinutes,
    getVehicleTrend,
  }
}