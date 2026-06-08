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

export interface AnalyticsChainKpi {
  period: string
  parkId?: number
  avgCompletionMinutes: number
  waitP50Minutes: number
  waitP90Minutes: number
  tasksPerVehiclePerDay: number
}

export function getAnalyticsChainKpi(period: 'day' | 'week' | 'month' = 'week', parkId?: number | null) {
  return request.get<any, ApiResponse<AnalyticsChainKpi>>('/admin/analytics/chain-kpi', {
    params: { period, ...parkParams(parkId) },
  })
}

export interface AnalyticsPeakCompare {
  normalMode: AnalyticsChainKpi
  peakMode: AnalyticsChainKpi
}

export function getAnalyticsPeakCompare(period: 'day' | 'week' | 'month' = 'week', parkId?: number | null) {
  return request.get<any, ApiResponse<AnalyticsPeakCompare>>('/admin/analytics/peak-compare', {
    params: { period, ...parkParams(parkId) },
  })
}

export function getAnalyticsPdfUrl(date?: string, parkId?: number | null) {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const params = new URLSearchParams()
  if (date) params.set('date', date)
  if (parkId != null && Number.isFinite(parkId) && parkId > 0) params.set('parkId', String(parkId))
  const qs = params.toString()
  return `${base}/api/admin/analytics/export/pdf${qs ? `?${qs}` : ''}`
}

export function getAnalyticsExportUrl(dataset: string, period = 'week', parkId?: number | null) {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const parkQuery = parkId != null && Number.isFinite(parkId) && parkId > 0 ? `&parkId=${parkId}` : ''
  return `${base}/api/admin/analytics/export/csv?dataset=${encodeURIComponent(dataset)}&period=${encodeURIComponent(period)}${parkQuery}`
}

export interface CustomReportRequest {
  metrics: string[]
  dateRange?: string[]
  dimension?: string | null
}

export interface CustomReportResponse {
  metrics: string[]
  dateRange?: string[]
  dimension?: string | null
  summary: Record<string, number>
  details?: Array<Record<string, any>>
}

export function generateCustomReport(data: CustomReportRequest, parkId?: number | null) {
  return request.post<any, ApiResponse<CustomReportResponse>>('/admin/analytics/custom-report', data, {
    params: parkId != null && Number.isFinite(parkId) && parkId > 0 ? { parkId } : undefined,
  })
}

export interface TimelineEvent {
  stage: string
  label: string
  time: string
  status: 'completed' | 'active' | 'pending' | 'error'
  remark: string
  refId?: number
}

export interface OrderTimeline {
  orderId: number
  orderNo: string
  status: string
  events: TimelineEvent[]
}

export function getOrderTimeline(orderId: number) {
  return request.get<any, ApiResponse<OrderTimeline>>('/admin/analytics/order-timeline', {
    params: { orderId },
  })
}

export interface ReportHistoryItem {
  id: number
  reportType: string
  reportName: string
  dataset: string | null
  period: string | null
  date: string | null
  parkId: number | null
  fileSizeBytes: number | null
  generatedBy: string | null
  generatedAt: string
}

export function getReportHistory(reportType?: string, limit = 50, offset = 0) {
  const params: Record<string, any> = { limit, offset }
  if (reportType) params.reportType = reportType
  return request.get<any, ApiResponse<ReportHistoryItem[]>>('/admin/analytics/report-history', { params })
}
