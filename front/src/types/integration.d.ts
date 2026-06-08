export interface WebhookSubscription {
  id: number
  name: string
  callbackUrl: string
  channelType?: string
  eventTypes: string
  enabled: boolean
  failureCount?: number
  lastDeliveryAt?: string
}

export interface WebhookUpsertPayload {
  id?: number
  name: string
  callbackUrl: string
  channelType?: string
  secretToken?: string
  eventTypes: string
  enabled?: boolean
}

export interface ExternalApiKey {
  id: number
  keyName: string
  apiKey: string
  status: string
  rateLimitPerMinute: number
  totalCalls?: number
  rateLimitHits?: number
  lastUsedAt?: string
}

// V5-I1: API 调用统计
export interface ApiCallStats {
  period: string
  totalCalls: number
  successRate: number
  errorRate: number
  p99LatencyMs: number
  p50LatencyMs: number
  uniqueKeys: number
  activeWebhooks: number
}

export interface ApiCallTrendPoint {
  date: string
  totalCalls: number
  successCount: number
  errorCount: number
  avgLatencyMs: number
}

export interface ApiTopEndpoint {
  method: string
  path: string
  callCount: number
  avgLatencyMs: number
  errorRate: number
}

export interface ApiCallStatsResponse {
  summary: ApiCallStats
  trend: ApiCallTrendPoint[]
  topEndpoints: ApiTopEndpoint[]
}

// V5-I2: Webhook 投递日志增强
export interface WebhookDeliveryFilter {
  subscriptionId?: number
  eventType?: string
  success?: boolean
  dateFrom?: string
  dateTo?: string
}

export interface WebhookDeliverySummary {
  total: number
  success: number
  failed: number
  pending: number
  avgLatencyMs: number
}

// V5-I3: API 沙箱
export interface SandboxApiKey {
  id: number
  keyName: string
  apiKey: string
  status: string
  environment: 'sandbox'
  totalCalls: number
  createdAt: string
  lastUsedAt?: string
}