import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type {
  AnalyticsChargingOverview,
  AnalyticsDailySummary,
  AnalyticsEfficiency,
  AnalyticsExceptionAnalysis,
  AnalyticsParkCompareItem,
} from '@/types/analytics'

export function getAnalyticsEfficiency(period: 'day' | 'week' | 'month' = 'week') {
  return request.get<any, ApiResponse<AnalyticsEfficiency>>('/admin/analytics/efficiency', {
    params: { period },
  })
}

export function getAnalyticsExceptions(period: 'day' | 'week' | 'month' = 'week') {
  return request.get<any, ApiResponse<AnalyticsExceptionAnalysis>>('/admin/analytics/exceptions', {
    params: { period },
  })
}

export function getAnalyticsDailySummary(date?: string) {
  return request.get<any, ApiResponse<AnalyticsDailySummary>>('/admin/analytics/daily-summary', {
    params: date ? { date } : undefined,
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

export function getAnalyticsExportUrl(dataset: string, period = 'week') {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  return `${base}/api/admin/analytics/export/csv?dataset=${encodeURIComponent(dataset)}&period=${encodeURIComponent(period)}`
}
