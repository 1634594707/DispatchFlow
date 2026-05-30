import request from '@/utils/request'
import type { ApiResponse } from '@/types/api'
import type { ExternalApiKey, WebhookSubscription, WebhookUpsertPayload } from '@/types/integration'

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
