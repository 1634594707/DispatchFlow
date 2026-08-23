/**
 * V5-Q3: Park order data composable
 *
 * Extracted from Tracking.vue order panel logic.
 * Manages park order list and map-specific order filtering.
 */
import { ref, computed } from 'vue'
import { getParkOrders } from '@/api/park'
import { filterSchematicOrders, filterGeoDeliveryOrders } from '@/maps/stationLayers'
import type { ParkOrderSnapshot } from '@/types/park'
import { useParkScopeStore } from '@/stores/parkScope'

export function useParkOrders() {
  const parkScope = useParkScopeStore()
  const parkOrders = ref<ParkOrderSnapshot[]>([])

  const schematicOrdersOnMap = computed(() => filterSchematicOrders(parkOrders.value))

  const geoOrdersOnMap = computed(() => filterGeoDeliveryOrders(parkOrders.value))

  async function refreshOrders() {
    const response = await getParkOrders({ parkId: parkScope.selectedParkId })
    parkOrders.value = response.data || []
  }

  return {
    parkOrders,
    schematicOrdersOnMap,
    geoOrdersOnMap,
    refreshOrders,
  }
}
