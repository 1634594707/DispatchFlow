import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { createDispatchStreamClient } from '@/utils/dispatchStreamClient'
import type { DispatchStreamClient } from '@/types/realtime'
import { useDashboardStore } from '@/stores/dashboard'
import { useWorkbenchStore } from '@/stores/workbench'
import { useAlertStore } from '@/stores/alert'
import { useParkScopeStore } from '@/stores/parkScope'

export const useRealtimeStore = defineStore('realtime', () => {
  const connected = ref(false)
  const degraded = ref(false)
  const lastEventAt = ref<string | null>(null)
  const lastSnapshotAt = ref<string | null>(null)
  const pageVisible = ref(typeof document === 'undefined' ? true : document.visibilityState === 'visible')
  let client: DispatchStreamClient | null = null
  let stopParkSubscription: (() => void) | null = null
  let fallbackTimer: ReturnType<typeof setInterval> | null = null
  let refreshNotifyTimer: ReturnType<typeof setTimeout> | null = null
  let visibilityHandler: (() => void) | null = null
  const refreshListeners = new Set<() => void | Promise<void>>()

  const status = computed(() => {
    if (connected.value) return 'connected'
    if (degraded.value) return 'degraded'
    return 'disconnected'
  })

  async function refreshFallback() {
    if (!degraded.value || !pageVisible.value) return
    const dashboardStore = useDashboardStore()
    const workbenchStore = useWorkbenchStore()
    await Promise.all([
      workbenchStore.fetchQueue({ silent: true }),
      dashboardStore.fetchSummary({ silent: true }),
    ])
    await notifyRefreshListeners()
  }

  async function notifyRefreshListeners() {
    await Promise.all([...refreshListeners].map((listener) => Promise.resolve(listener())))
  }

  function scheduleRefreshListeners() {
    if (refreshNotifyTimer) return
    refreshNotifyTimer = setTimeout(() => {
      refreshNotifyTimer = null
      void notifyRefreshListeners()
    }, 250)
  }

  function subscribeRefresh(listener: () => void | Promise<void>) {
    refreshListeners.add(listener)
    return () => refreshListeners.delete(listener)
  }

  function stopFallbackPolling() {
    if (fallbackTimer) {
      clearInterval(fallbackTimer)
      fallbackTimer = null
    }
  }

  function startFallbackPolling() {
    if (fallbackTimer || !pageVisible.value) return
    // SSE 断开时统一由 realtime store 降级刷新，避免页面各自维护定时器。
    void refreshFallback()
    fallbackTimer = setInterval(() => { void refreshFallback() }, 30_000)
  }

  function markConnected() {
    connected.value = true
    degraded.value = false
    stopFallbackPolling()
  }

  function markDisconnected() {
    connected.value = false
    degraded.value = true
    startFallbackPolling()
  }

  function createClient(parkId: number | undefined) {
    const dashboardStore = useDashboardStore()
    const workbenchStore = useWorkbenchStore()
    const alertStore = useAlertStore()
    return createDispatchStreamClient({
      onOpen: markConnected,
      onClose: markDisconnected,
      onDashboard: (summary) => {
        lastSnapshotAt.value = new Date().toISOString()
        dashboardStore.applySummary(summary)
      },
      onWorkbench: (payload) => {
        lastSnapshotAt.value = payload.ts || new Date().toISOString()
      },
      onWorkbenchRefresh: (payload) => {
        lastEventAt.value = payload.ts || new Date().toISOString()
        workbenchStore.fetchQueue({ silent: true })
        scheduleRefreshListeners()
      },
      onDashboardRefresh: (payload) => {
        lastEventAt.value = payload.ts || new Date().toISOString()
        dashboardStore.fetchSummary({ silent: true })
        scheduleRefreshListeners()
      },
      onException: (payload) => {
        lastEventAt.value = payload.eventTime || payload.ts || new Date().toISOString()
        alertStore.handleExceptionAlert(payload)
        workbenchStore.fetchQueue({ silent: true })
        scheduleRefreshListeners()
      },
      onEvent: (payload) => {
        lastEventAt.value = payload.eventTime || payload.ts || new Date().toISOString()
        scheduleRefreshListeners()
      },
      onPing: (payload) => {
        lastEventAt.value = payload.ts || new Date().toISOString()
      },
    }, parkId)
  }

  function start() {
    if (client) return
    const alertStore = useAlertStore()
    const parkScope = useParkScopeStore()

    client = createClient(parkScope.selectedParkId)
    client.start()
    stopParkSubscription = parkScope.$subscribe((_mutation, state) => {
      const nextParkId = state.selectedParkId
      if (!client) return
      client.stop()
      client = createClient(nextParkId)
      client.start()
    })
    alertStore.ensureNotifyPermission()
    if (typeof document !== 'undefined') {
      visibilityHandler = () => {
        pageVisible.value = document.visibilityState === 'visible'
        if (pageVisible.value && degraded.value) {
          void refreshFallback()
          startFallbackPolling()
        } else if (!pageVisible.value) {
          stopFallbackPolling()
        }
      }
      document.addEventListener('visibilitychange', visibilityHandler)
    }
  }

  function stop() {
    client?.stop()
    stopParkSubscription?.()
    stopParkSubscription = null
    stopFallbackPolling()
    if (refreshNotifyTimer) {
      clearTimeout(refreshNotifyTimer)
      refreshNotifyTimer = null
    }
    if (visibilityHandler && typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', visibilityHandler)
    }
    visibilityHandler = null
    client = null
    connected.value = false
    degraded.value = false
    refreshListeners.clear()
  }

  /**
   * 阶段八 8.2：销毁 realtime 流客户端，用于 BasicLayout onUnmounted 最终清理。
   * 调用后需要重新调用 start() 创建新客户端。
   */
  function destroy() {
    client?.destroy()
    stopParkSubscription?.()
    stopParkSubscription = null
    stopFallbackPolling()
    if (refreshNotifyTimer) {
      clearTimeout(refreshNotifyTimer)
      refreshNotifyTimer = null
    }
    if (visibilityHandler && typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', visibilityHandler)
    }
    visibilityHandler = null
    client = null
    connected.value = false
    degraded.value = false
    refreshListeners.clear()
  }

  return {
    connected,
    degraded,
    status,
    pageVisible,
    lastEventAt,
    lastSnapshotAt,
    start,
    stop,
    destroy,
    subscribeRefresh,
  }
})
