import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDashboardSummary } from '@/api/dashboard'
import type { DashboardSummary } from '@/types/dashboard'

export const useDashboardStore = defineStore('dashboard', () => {
  const summary = ref<DashboardSummary | null>(null)
  const loading = ref(false)
  const lastUpdated = ref<string>('')

  async function fetchSummary() {
    loading.value = true
    try {
      const res = await getDashboardSummary()
      summary.value = res.data
      lastUpdated.value = new Date().toLocaleTimeString('zh-CN')
    } catch (e) {
      console.error('Failed to fetch dashboard summary', e)
    } finally {
      loading.value = false
    }
  }

  return { summary, loading, lastUpdated, fetchSummary }
})
