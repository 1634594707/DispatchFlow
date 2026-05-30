export interface WebhookSubscription {
  id: number
  name: string
  callbackUrl: string
  eventTypes: string
  enabled: boolean
  failureCount?: number
  lastDeliveryAt?: string
}

export interface WebhookUpsertPayload {
  id?: number
  name: string
  callbackUrl: string
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
  lastUsedAt?: string
}
