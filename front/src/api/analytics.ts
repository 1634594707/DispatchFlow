import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type {
  AnalyticsChargingOverview,
  AnalyticsDailySummary,
  AnalyticsEfficiency,
  AnalyticsExceptionAnalysis,
  AnalyticsParkCompareItem,
} from '@/types/analytics'

function parkParams(parkId?: number | null) {
  return parkId != null && Number.isFinite(parkId) && parkId > 0 ? { parkId } : undefined
}

export function getAnalyticsEfficiency(period: 'day' | 'week' | 'month' = 'week', parkId?: number | null) {
  return request.get<any, ApiResponse<AnalyticsEfficiency>>('/admin/analytics/efficiency', {
    params: { period, ...parkParams(parkId) },
  })
}

export function getAnalyticsExceptions(period: 'day' | 'week' | 'month' = 'week', parkId?: number | null) {
  return request.get<any, ApiResponse<AnalyticsExceptionAnalysis>>('/admin/analytics/exceptions', {
    params: { period, ...parkParams(parkId) },
  })
}

export function getAnalyticsDailySummary(date?: string, parkId?: number | null) {
  return request.get<any, ApiResponse<AnalyticsDailySummary>>('/admin/analytics/daily-summary', {
    params: date ? { date, ...parkParams(parkId) } : parkParams(parkId),
  })
}

export function getAnalyticsChargingOverview() {
  return request.get<any, ApiResponse<AnalyticsChargingOverview>>('/admin/analytics/charging')
}

export function getAnalyticsParkComparison(period: 'day' | 'week' | 'month' = 'week') {
  return request.get<any, ApiResponse<AnalyticsParkCompareItem[]>>('/admin/analytics/park-comparison', {
    params: { period },
  })
}

export function getAnalyticsExportUrl(dataset: string, period = 'week', parkId?: number | null) {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const parkQuery = parkId != null && Number.isFinite(parkId) && parkId > 0 ? `&parkId=${parkId}` : ''
  return `${base}/api/admin/analytics/export/csv?dataset=${encodeURIComponent(dataset)}&period=${encodeURIComponent(period)}${parkQuery}`
}
