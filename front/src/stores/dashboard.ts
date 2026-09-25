import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDashboardSummary } from '@/api/dashboard'
import type { DashboardSummary } from '@/types/dashboard'
import { useParkScopeStore } from '@/stores/parkScope'

export const useDashboardStore = defineStore('dashboard', () => {
  const parkScope = useParkScopeStore()
  const summary = ref<DashboardSummary | null>(null)
  const loading = ref(false)
  const lastUpdated = ref<string>('')

  /** §6.5：与 workbench 队列同理 —— 布局角标、页面首屏、降级兜底会同时各要一次。 */
  let summaryInFlight: Promise<void> | null = null

  function fetchSummary(options?: { silent?: boolean; force?: boolean }) {
    if (!options?.force && summaryInFlight) return summaryInFlight
    const run = loadSummary(options)
    summaryInFlight = run
    void run.finally(() => {
      if (summaryInFlight === run) summaryInFlight = null
    })
    return run
  }

  async function loadSummary(options?: { silent?: boolean }) {
    if (!options?.silent) {
      loading.value = true
    }
    try {
      const res = await getDashboardSummary(parkScope.selectedParkId)
      summary.value = res.data
      lastUpdated.value = new Date().toLocaleTimeString('zh-CN')
    } catch (e) {
      console.error('Failed to fetch dashboard summary', e)
    } finally {
      if (!options?.silent) {
        loading.value = false
      }
    }
  }

  function applySummary(next: DashboardSummary) {
    if (next.parkId != null && next.parkId !== parkScope.selectedParkId) return
    summary.value = next
    lastUpdated.value = new Date().toLocaleTimeString('zh-CN')
  }

  return { summary, loading, lastUpdated, fetchSummary, applySummary }
})
