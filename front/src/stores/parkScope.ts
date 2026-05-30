import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { listParks } from '@/api/park'
import type { ParkSummary } from '@/types/park'

const STORAGE_KEY = 'fsd_selected_park_id'

export const useParkScopeStore = defineStore('parkScope', () => {
  const parks = ref<ParkSummary[]>([])
  const loading = ref(false)
  const selectedParkId = ref<number | undefined>(loadStoredParkId())
  /** Incremented on park change so pages can watch and refresh scoped data. */
  const scopeVersion = ref(0)

  const parkOptions = computed(() => [
    { label: '全部园区', value: undefined as number | undefined },
    ...parks.value.map((p) => ({ label: p.parkName, value: p.parkId })),
  ])

  const selectedParkName = computed(() => {
    if (selectedParkId.value == null) return '全部园区'
    return parks.value.find((p) => p.parkId === selectedParkId.value)?.parkName ?? '全部园区'
  })

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
    const changed = selectedParkId.value !== parkId
    selectedParkId.value = parkId
    if (parkId == null) {
      localStorage.removeItem(STORAGE_KEY)
    } else {
      localStorage.setItem(STORAGE_KEY, String(parkId))
    }
    if (changed) {
      scopeVersion.value += 1
    }
  }

  /** Resolve map/layout park when header scope is "全部园区". */
  function resolveLayoutParkId(): number | undefined {
    if (selectedParkId.value != null) return selectedParkId.value
    const defaultPark = parks.value.find((p) => p.defaultPark) || parks.value[0]
    return defaultPark?.parkId
  }

  /** Clear invalid stored park id (inactive / deleted). */
  function ensureValidSelection() {
    if (selectedParkId.value != null
        && !parks.value.some((p) => p.parkId === selectedParkId.value)) {
      setParkId(undefined)
    }
  }

  async function loadParks() {
    loading.value = true
    try {
      parks.value = (await listParks()).data
      ensureValidSelection()
    } finally {
      loading.value = false
    }
  }

  return {
    parks,
    loading,
    selectedParkId,
    scopeVersion,
    parkOptions,
    selectedParkName,
    setParkId,
    resolveLayoutParkId,
    ensureValidSelection,
    loadParks,
  }
})
