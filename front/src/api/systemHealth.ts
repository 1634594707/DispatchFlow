import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { DetailedMetricsResponse, HealthTimelineResponse, SystemHealthResponse } from '@/types/phase10'

export function getSystemHealth() {
  return request.get<any, ApiResponse<SystemHealthResponse>>('/admin/system/health')
}

export function getDetailedMetrics() {
  return request.get<any, ApiResponse<DetailedMetricsResponse>>('/admin/system/health/metrics')
}

export function getHealthTimeline() {
  return request.get<any, ApiResponse<HealthTimelineResponse>>('/admin/system/health/timeline')
}
