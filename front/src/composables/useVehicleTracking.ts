/**
 * V5-Q3: Vehicle data & filtering composable
 *
 * Extracted from Tracking.vue vehicle panel logic.
 * Manages vehicle list, status filtering, and summary counts.
 */
import { ref, computed } from 'vue'
import { getParkVehicles } from '@/api/park'
import type { ParkVehicleSnapshot } from '@/types/park'

export function useVehicleTracking() {
  const vehicles = ref<ParkVehicleSnapshot[]>([])
  const activeFilter = ref('all')

  const onlineCount = computed(() => vehicles.value.filter(v => v.onlineStatus === 'ONLINE').length)
  const busyCount = computed(() => vehicles.value.filter(v => v.dispatchStatus === 'BUSY').length)
  const chargingCount = computed(() => vehicles.value.filter(v => v.charging).length)
  const lowBatteryCount = computed(() => vehicles.value.filter(v => v.lowBattery).length)

  const filteredVehicles = computed(() => {
    switch (activeFilter.value) {
      case 'ONLINE':
        return vehicles.value.filter(v => v.onlineStatus === 'ONLINE')
      case 'OFFLINE':
        return vehicles.value.filter(v => v.onlineStatus === 'OFFLINE')
      case 'BUSY':
        return vehicles.value.filter(v => v.dispatchStatus === 'BUSY')
      case 'CHARGING':
        return vehicles.value.filter(v => v.charging)
      case 'LOW_BATTERY':
        return vehicles.value.filter(v => v.lowBattery)
      case 'SIM':
        return vehicles.value.filter(v => v.linkMode !== 'REAL' && v.linkMode !== 'VDA5050')
      case 'REAL':
        return vehicles.value.filter(v => v.linkMode === 'REAL')
      case 'VDA5050':
        return vehicles.value.filter(v => v.linkMode === 'VDA5050')
      default:
        return vehicles.value
    }
  })

  function filterByStatus(status: string) {
    activeFilter.value = status
  }

  async function refreshVehicles() {
    const response = await getParkVehicles()
    vehicles.value = response.data || []
  }

  return {
    vehicles,
    activeFilter,
    onlineCount,
    busyCount,
    chargingCount,
    lowBatteryCount,
    filteredVehicles,
    filterByStatus,
    refreshVehicles,
  }
}