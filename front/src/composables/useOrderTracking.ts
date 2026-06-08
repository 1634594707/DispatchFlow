/**
 * V5-Q3: Order tracking panel state composable
 *
 * Extracted from ParkOrder.vue tracking panel logic.
 * Manages tracked order selection and derived geo display state.
 */
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { buildGeofencePolygons } from '@/maps'
import { filterSchematicOrders, filterGeoDeliveryOrders, filterSchematicParkVehicles, filterGeoDeliverySimVehicles } from '@/maps/stationLayers'
import { formatDeliveryEta, formatDistance, polylineLengthMeters } from '@/maps/geoDistance'
import { routeAnomalyWarning } from '@/maps/routeValidation'
import { buildGeoTrackingLink } from '@/constants/parkDelivery'
import type { ParkOrderSnapshot, ParkVehicleSnapshot, ParkGeofence, ParkLayout } from '@/types/park'
import type { MobileOrderMode } from '@/constants/parkDelivery'
import type { Ref } from 'vue'

export function useOrderTracking(options: {
  orderMode: Ref<MobileOrderMode>
  parkOrders: Ref<ParkOrderSnapshot[]>
  vehicles: Ref<ParkVehicleSnapshot[]>
  parkGeofences: Ref<ParkGeofence[]>
  // parkLayout is reserved for future map center logic
  _parkLayout?: Ref<ParkLayout | null>
}) {
  const { orderMode, parkOrders, vehicles, parkGeofences } = options
  const trackedOrderId = ref<number | null>(null)
  const lastToastStage = ref<string | null>(null)

  const visibleParkOrders = computed(() =>
    orderMode.value === 'schematic'
      ? filterSchematicOrders(parkOrders.value)
      : filterGeoDeliveryOrders(parkOrders.value),
  )

  const activeOrders = computed(() =>
    visibleParkOrders.value.filter(order => !['COMPLETED', 'FAILED'].includes(order.runtimeStage)),
  )

  const modeVehicles = computed(() =>
    orderMode.value === 'schematic'
      ? filterSchematicParkVehicles(vehicles.value)
      : filterGeoDeliverySimVehicles(vehicles.value),
  )

  const trackedOrder = computed(() => {
    if (trackedOrderId.value) {
      const matched = visibleParkOrders.value.find(order => order.orderId === trackedOrderId.value)
      if (matched) return matched
    }
    return activeOrders.value[0] || visibleParkOrders.value[0] || null
  })

  const trackedVehicle = computed(() => {
    if (!trackedOrder.value?.vehicleId) return null
    return modeVehicles.value.find(vehicle => vehicle.vehicleId === trackedOrder.value?.vehicleId) || null
  })

  const routeAnomalyText = computed(() => routeAnomalyWarning(modeVehicles.value))

  const trackingGeoPolygons = computed(() => buildGeofencePolygons(parkGeofences.value))

  const trackingScreenLink = computed(() =>
    buildGeoTrackingLink(trackedOrder.value?.orderId, trackedOrder.value?.vehicleId),
  )

  const remainingDeliveryLabel = computed(() => {
    const vehicle = trackedVehicle.value
    if (!vehicle || trackedOrder.value?.runtimeStage === 'COMPLETED') return null

    const planned = vehicle.plannedRouteGeo
    if (planned && planned.length >= 2) {
      const path = planned.map(
        point => [Number(point.longitude ?? point.x), Number(point.latitude ?? point.y)] as [number, number],
      )
      const meters = polylineLengthMeters(path)
      if (meters > 0) return `剩余 ${formatDistance(meters)} · ${formatDeliveryEta(meters)}`
    }

    const order = trackedOrder.value
    if (order?.pickupStation.coordLng != null && order.dropoffStation.coordLng != null) {
      const path: [number, number][] = [
        [Number(order.pickupStation.coordLng), Number(order.pickupStation.coordLat)],
        [Number(order.dropoffStation.coordLng), Number(order.dropoffStation.coordLat)],
      ]
      const meters = polylineLengthMeters(path)
      return `全程约 ${formatDistance(meters)} · ${formatDeliveryEta(meters)}`
    }

    return null
  })

  function watchStageChanges() {
    // Watch handler to be wired externally; extracted for testability
    const stage = trackedOrder.value?.runtimeStage
    const previous = lastToastStage.value
    if (!stage || stage === lastToastStage.value) return
    if (stage === 'COMPLETED' && previous !== 'COMPLETED') {
      message.success({ content: '货物已送达，无人车返回待命区', duration: 5 })
    }
    if (stage === 'HEADING_TO_DROPOFF' && previous === 'LOADING') {
      message.info('已取货，正在沿道路配送至送货点')
    }
    lastToastStage.value = stage
  }

  return {
    trackedOrderId,
    lastToastStage,
    visibleParkOrders,
    activeOrders,
    modeVehicles,
    trackedOrder,
    trackedVehicle,
    routeAnomalyText,
    trackingGeoPolygons,
    trackingScreenLink,
    remainingDeliveryLabel,
    watchStageChanges,
  }
}