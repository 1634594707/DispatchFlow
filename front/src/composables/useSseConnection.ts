/**
 * V5-Q3: SSE stream management composable
 *
 * Extracted from Tracking.vue SSE lifecycle logic.
 * Manages stream connection, reconnection, and fallback polling.
 */
import { ref, computed, onBeforeUnmount, type Ref } from 'vue'
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

    sseClient = createSSEClient({
      url: () => getFleetTelemetryStreamUrl(effectiveParkId.value ?? undefined),
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

  /**
   * 阶段八 8.2：组件卸载时销毁 SSE 客户端，防止内存泄漏与幽灵连接。
   * 使用 destroy() 而非 stop()，明确表达"不再使用"的语义，
   * 并防止组件被复用时误调用 start()。
   */
  function destroyStream() {
    if (sseClient) {
      sseClient.destroy()
      sseClient = null
    }
    streamConnected.value = false
    sseReconnecting.value = false
  }

  // 组件卸载时销毁 EventSource，防止内存泄漏与幽灵连接
  onBeforeUnmount(destroyStream)

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
