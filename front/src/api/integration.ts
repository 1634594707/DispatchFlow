import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type {
  ExternalApiKey,
  ApiCallStatsResponse,
  SandboxApiKey,
  WebhookSubscription,
  WebhookUpsertPayload,
} from '@/types/integration'

export function fetchWebhooks() {
  return request.get<any, ApiResponse<WebhookSubscription[]>>('/admin/integration/webhooks')
}

export function saveWebhook(payload: WebhookUpsertPayload) {
  return request.post<any, ApiResponse<WebhookSubscription>>('/admin/integration/webhooks', payload)
}

export function deleteWebhook(id: number) {
  return request.delete<any, ApiResponse<void>>(`/admin/integration/webhooks/${id}`)
}

export function fetchApiKeys() {
  return request.get<any, ApiResponse<ExternalApiKey[]>>('/admin/integration/api-keys')
}

export function createApiKey(keyName: string, rateLimitPerMinute = 120) {
  return request.post<any, ApiResponse<ExternalApiKey>>('/admin/integration/api-keys', {
    keyName,
    rateLimitPerMinute,
  })
}

export function disableApiKey(id: number) {
  return request.post<any, ApiResponse<void>>(`/admin/integration/api-keys/${id}/disable`)
}

export interface WebhookDeliveryLog {
  id: number
  subscriptionId: number
  eventType: string
  businessKey?: string
  httpStatus?: number
  success: boolean
  attemptNo: number
  payloadSummary?: string
  errorMessage?: string
  deliveredAt: string
}

export function fetchWebhookDeliveries(subscriptionId: number, limit = 50) {
  return request.get<any, ApiResponse<WebhookDeliveryLog[]>>(
    `/admin/integration/webhooks/${subscriptionId}/deliveries`,
    { params: { limit } },
  )
}

export function testWebhook(id: number) {
  return request.post<any, ApiResponse<void>>(`/admin/integration/webhooks/${id}/test`)
}

export const DISPATCH_EVENT_OPTIONS = [
  { label: '任务创建', value: 'dispatch.task.created' },
  { label: '任务已派车', value: 'dispatch.task.assigned' },
  { label: '任务执行中', value: 'dispatch.task.executing' },
  { label: '任务成功', value: 'dispatch.task.success' },
  { label: '任务失败', value: 'dispatch.task.failed' },
  { label: '任务取消', value: 'dispatch.task.cancelled' },
  { label: '异常打开', value: 'dispatch.exception.open' },
  { label: '异常已解决', value: 'dispatch.exception.resolved' },
  { label: '枢纽到达', value: 'dispatch.hub.arrival' },
  { label: '枢纽离开', value: 'dispatch.hub.departure' },
]

export const WEBHOOK_PRESETS = {
  wms: ['dispatch.task.created', 'dispatch.task.success', 'dispatch.task.failed'],
  mes: ['dispatch.task.executing', 'dispatch.exception.open', 'dispatch.exception.resolved'],
}

// V5-I1: API 调用统计
export function fetchApiCallStats(days: 7 | 30 = 7) {
  return request.get<any, ApiResponse<ApiCallStatsResponse>>('/admin/integration/api-stats', {
    params: { days },
  })
}

// V5-I2: Webhook 投递日志全量查询
export function fetchAllDeliveryLogs(params?: {
  subscriptionId?: number
  eventType?: string
  success?: boolean
  limit?: number
}) {
  return request.get<any, ApiResponse<WebhookDeliveryLog[]>>('/admin/integration/deliveries', {
    params,
  })
}

export function retryWebhookDelivery(deliveryId: number) {
  return request.post<any, ApiResponse<void>>(`/admin/integration/deliveries/${deliveryId}/retry`)
}

// V5-I3: API 沙箱
export function fetchSandboxKeys() {
  return request.get<any, ApiResponse<SandboxApiKey[]>>('/admin/integration/sandbox/keys')
}

export function createSandboxKey(keyName: string) {
  return request.post<any, ApiResponse<SandboxApiKey>>('/admin/integration/sandbox/keys', {
    keyName,
  })
}

export function disableSandboxKey(id: number) {
  return request.post<any, ApiResponse<void>>(`/admin/integration/sandbox/keys/${id}/disable`)
}