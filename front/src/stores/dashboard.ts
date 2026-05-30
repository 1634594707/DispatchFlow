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

  async function fetchSummary(options?: { silent?: boolean }) {
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
    summary.value = next
    lastUpdated.value = new Date().toLocaleTimeString('zh-CN')
  }

  return { summary, loading, lastUpdated, fetchSummary, applySummary }
})
