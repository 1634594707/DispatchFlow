import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { SystemHealthResponse, DetailedMetricsResponse, HealthTimelineResponse } from '@/types/phase10'

export function getSystemHealth() {
  return request.get<any, ApiResponse<SystemHealthResponse>>('/admin/system/health')
}

/** V5-S3: 获取详细指标（MQ堆积、连接池、Redis、SSE、API P99） */
export function getDetailedMetrics() {
  return request.get<any, ApiResponse<DetailedMetricsResponse>>('/admin/system/health/metrics')
}

/** V5-S3: 获取健康时间线 */
export function getHealthTimeline() {
  return request.get<any, ApiResponse<HealthTimelineResponse>>('/admin/system/health/timeline')
}