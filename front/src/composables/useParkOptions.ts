import { ref, computed, onMounted } from 'vue'
import * as infraApi from '@/api/infrastructure'
import type { AdminPark } from '@/types/infrastructure'

export function useParkOptions() {
  const parks = ref<AdminPark[]>([])
  const loading = ref(false)

  const parkOptions = computed(() =>
    parks.value.map((p) => ({ label: p.parkName, value: p.id }))
  )

  async function loadParks() {
    loading.value = true
    try {
      parks.value = (await infraApi.fetchParks()).data
    } finally {
      loading.value = false
    }
  }

  onMounted(loadParks)

  return { parks, parkOptions, loading, loadParks }
}
