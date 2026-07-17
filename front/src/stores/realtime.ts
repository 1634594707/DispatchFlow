import { defineStore } from 'pinia'
import { ref } from 'vue'
import { createDispatchStreamClient } from '@/utils/dispatchStreamClient'
import type { DispatchStreamClient } from '@/types/realtime'
import { useDashboardStore } from '@/stores/dashboard'
import { useWorkbenchStore } from '@/stores/workbench'
import { useAlertStore } from '@/stores/alert'

export const useRealtimeStore = defineStore('realtime', () => {
  const connected = ref(false)
  let client: DispatchStreamClient | null = null

  function start() {
    if (client) return
    const dashboardStore = useDashboardStore()
    const workbenchStore = useWorkbenchStore()
    const alertStore = useAlertStore()

    client = createDispatchStreamClient({
      onOpen: () => {
        connected.value = true
      },
      onClose: () => {
        connected.value = false
      },
      onDashboard: (summary) => {
        dashboardStore.applySummary(summary)
      },
      onWorkbench: () => {
        // Counts refreshed via workbench-refresh or full fetch
      },
      onWorkbenchRefresh: () => {
        workbenchStore.fetchQueue({ silent: true })
      },
      onDashboardRefresh: () => {
        dashboardStore.fetchSummary({ silent: true })
      },
      onException: (payload) => {
        alertStore.handleExceptionAlert(payload)
        workbenchStore.fetchQueue({ silent: true })
      },
    })
    client.start()
    alertStore.ensureNotifyPermission()
  }

  function stop() {
    client?.stop()
    client = null
    connected.value = false
  }

  /**
   * 阶段八 8.2：销毁 realtime 流客户端，用于 BasicLayout onUnmounted 最终清理。
   * 调用后需要重新调用 start() 创建新客户端。
   */
  function destroy() {
    client?.destroy()
    client = null
    connected.value = false
  }

  return { connected, start, stop, destroy }
})
