import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as infraApi from '@/api/infrastructure'
import type { AdminPark } from '@/types/infrastructure'

const STORAGE_KEY = 'fsd_selected_park_id'

export const useParkScopeStore = defineStore('parkScope', () => {
  const parks = ref<AdminPark[]>([])
  const loading = ref(false)
  const selectedParkId = ref<number | undefined>(loadStoredParkId())

  const parkOptions = computed(() => [
    { label: '全部园区', value: undefined as number | undefined },
    ...parks.value.map((p) => ({ label: p.parkName, value: p.id })),
  ])

  function loadStoredParkId(): number | undefined {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return undefined
      const id = Number(raw)
      return Number.isFinite(id) ? id : undefined
    } catch {
      return undefined
    }
  }

  function setParkId(parkId: number | undefined) {
    selectedParkId.value = parkId
    if (parkId == null) {
      localStorage.removeItem(STORAGE_KEY)
    } else {
      localStorage.setItem(STORAGE_KEY, String(parkId))
    }
  }

  async function loadParks() {
    loading.value = true
    try {
      parks.value = (await infraApi.fetchParks()).data
      if (selectedParkId.value != null
          && !parks.value.some((p) => p.id === selectedParkId.value)) {
        setParkId(undefined)
      }
    } finally {
      loading.value = false
    }
  }

  return {
    parks,
    loading,
    selectedParkId,
    parkOptions,
    setParkId,
    loadParks,
  }
})
