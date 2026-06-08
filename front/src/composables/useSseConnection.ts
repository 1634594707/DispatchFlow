/**
 * V5-Q3: SSE stream management composable
 *
 * Extracted from Tracking.vue SSE lifecycle logic.
 * Manages stream connection, reconnection, and fallback polling.
 */
import { ref, computed, type Ref } from 'vue'
import { getFleetTelemetryStreamUrl } from '@/api/dispatch'
import { createSSEClient } from '@/utils/sseClient'
import type { SSEClient } from '@/types/stream'
import type { ParkVehicleSnapshot } from '@/types/park'

export interface StreamPayload {
  ts?: string
  vehicles?: ParkVehicleSnapshot[]
  parkId?: number
}

export function useSseConnection(options?: {
  onVehicleUpdate?: (vehicles: ParkVehicleSnapshot[]) => void
}) {
  const streamConnected = ref(false)
  const sseReconnecting = ref(false)
  const lastStreamLatencyMs = ref<number | null>(null)
  const lastStreamAt = ref<string | null>(null)

  let sseClient: SSEClient | null = null

  const sseStatus = computed(() => {
    if (streamConnected.value) return 'connected'
    if (sseReconnecting.value) return 'reconnecting'
    return 'disconnected'
  })

  const sseStatusLabel = computed(() => {
    if (streamConnected.value) return '已连接'
    if (sseReconnecting.value) return '重连中'
    return '已断连'
  })

  function applyStreamPayload(data: StreamPayload) {
    if (data.ts) {
      lastStreamAt.value = data.ts
      const parsed = Date.parse(data.ts)
      if (!Number.isNaN(parsed)) {
        lastStreamLatencyMs.value = Math.max(0, Date.now() - parsed)
      }
    }
    if (data.vehicles && Array.isArray(data.vehicles)) {
      streamConnected.value = true
      sseReconnecting.value = false
      options?.onVehicleUpdate?.(data.vehicles)
    }
  }

  function startStream(effectiveParkId: Ref<number | null>) {
    if (sseClient) {
      sseClient.stop()
    }

    const streamUrl = getFleetTelemetryStreamUrl(effectiveParkId.value ?? undefined)

    sseClient = createSSEClient({
      url: streamUrl,
      eventName: 'telemetry',
      onMessage: applyStreamPayload,
      onOpen: () => {
        streamConnected.value = true
        sseReconnecting.value = false
      },
      onError: () => {
        streamConnected.value = false
        sseReconnecting.value = true
      },
      onClose: () => {
        streamConnected.value = false
        sseReconnecting.value = true
      },
      maxRetries: 10,
      baseDelay: 1000,
      maxDelay: 30000,
    })

    sseClient.start()
  }

  function stopStream() {
    if (sseClient) {
      sseClient.stop()
      sseClient = null
    }
    streamConnected.value = false
    sseReconnecting.value = false
  }

  return {
    streamConnected,
    sseReconnecting,
    lastStreamLatencyMs,
    lastStreamAt,
    sseStatus,
    sseStatusLabel,
    startStream,
    stopStream,
  }
}