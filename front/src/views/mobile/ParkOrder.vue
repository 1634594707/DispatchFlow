<template>
  <div class="mobile-order-page">
    <header class="mobile-header">
      <router-link class="header-back" to="/vehicle-tracking">← 大屏</router-link>
      <div class="header-brand">
        <span class="header-eyebrow">找家纺网 · 叠石桥 L1 试点</span>
        <h1>像看外卖一样看短驳配送</h1>
      </div>
      <div class="header-stats">
        <span>{{ orderableStationCount }} 站</span>
        <span>{{ activeOrders.length }} 单</span>
        <span>{{ vehicles.length }} 车</span>
      </div>
    </header>

    <main class="mobile-main">
      <OrderTrackingPanel
        v-if="trackedOrder"
        ref="trackingPanelRef"
        :order="trackedOrder"
        :active-orders="activeOrders"
        :vehicle="trackedVehicle"
        :park-layout="parkLayout"
        :geo-map-available="geoMapAvailable"
        :force-schematic-map="orderMode === 'schematic'"
        :map-center="trackingMapCenter"
        :geo-markers="trackingGeoMarkers"
        :geo-polylines="trackingGeoPolylines"
        :geo-polygons="trackingGeoPolygons"
        :fit-view-points="trackingFitViewPoints"
        :route-anomaly-text="routeAnomalyText"
        :screen-link="trackingScreenLink"
        :remaining-label="remainingDeliveryLabel"
        @select-order="trackedOrderId = $event"
        @order-again="scrollToQuickOrder"
      />

      <QuickOrderPanel
        ref="quickOrderPanelRef"
        :stations="stations"
        :submitting="submitting"
        :park-locked="isSinglePark"
        :park-name="lockedParkName"
        :park-id="form.parkId"
        :order-mode="orderMode"
        :demo-routes="activeDemoRoutes"
        :pickup-station-id="form.pickupStationId"
        :dropoff-station-id="form.dropoffStationId"
        :priority="form.priority || 'P1'"
        :remark="form.remark || ''"
        :loading-parks="loadingParks"
        :loading-stations="loadingStations"
        :park-options="parkOptions"
        @update:park-id="handleParkIdUpdate"
        @update:order-mode="handleOrderModeUpdate"
        @update:pickup-station-id="form.pickupStationId = $event"
        @update:dropoff-station-id="form.dropoffStationId = $event"
        @update:priority="form.priority = $event"
        @update:remark="form.remark = $event"
        @submit-demo="submitDemoRoute"
        @submit-custom="submitOrder"
      />

      <details v-if="showApiKeySettings" class="settings-panel">
        <summary>开发者设置 · API Key</summary>
        <label class="api-key-field" for="mobile-api-key">
          <span>X-Mobile-Api-Key</span>
          <input
            id="mobile-api-key"
            v-model="mobileApiKey"
            type="password"
            autocomplete="off"
            placeholder="留空则使用 VITE_MOBILE_API_KEY"
            @change="persistMobileApiKey"
          />
        </label>
        <p class="api-key-note">仅本地开发可见；生产环境请配置环境变量。</p>
      </details>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import OrderTrackingPanel from '@/components/mobile/OrderTrackingPanel.vue'
import QuickOrderPanel from '@/components/mobile/QuickOrderPanel.vue'
import { parkDeliveryDemoRoutes, parkSchematicDemoRoutes, loadMobileOrderMode, persistMobileOrderMode } from '@/constants/parkDelivery'
import type { MobileOrderMode } from '@/constants/parkDelivery'
import {
  createParkOrder,
  getParkGeofences,
  getParkLayout,
  getParkOrders,
  getParkStations,
  getParkVehicles,
  listParks,
} from '@/api/park'
import {
  buildGeofencePolygons,
  buildGeoPolylines,
  buildStationGeoMarkers,
  collectRouteFitPoints,
  filterGeoDeliveryOrders,
  filterGeoDeliverySimVehicles,
  filterSchematicOrders,
  filterSchematicParkVehicles,
  findMobileOrderStation,
  orderableStationsForMode,
  syncDefaultOrderStations,
  SCHEMATIC_ORDERABLE_STATION_COUNT,
  ZJF_ORDERABLE_STATION_COUNT,
  isAmapConfigured,
  pilotMapCenter,
  toAvGeoMarker,
  vehicleGeoPosition,
} from '@/maps'
import { formatDeliveryEta, formatDistance, polylineLengthMeters } from '@/maps/geoDistance'
import { buildGeoTrackingLink } from '@/constants/parkDelivery'
import { routeAnomalyWarning } from '@/maps/routeValidation'
import type {
  ParkGeofence,
  ParkLayout,
  ParkOrderCreateRequest,
  ParkOrderCreateResponse,
  ParkOrderSnapshot,
  ParkStation,
  ParkSummary,
  ParkVehicleSnapshot,
} from '@/types/park'

const loadingParks = ref(false)
const loadingStations = ref(false)
const parks = ref<ParkSummary[]>([])
const submitting = ref(false)
const stations = ref<ParkStation[]>([])
const vehicles = ref<ParkVehicleSnapshot[]>([])
const parkOrders = ref<ParkOrderSnapshot[]>([])
const parkLayout = ref<ParkLayout | null>(null)
const parkGeofences = ref<ParkGeofence[]>([])
const lastCreatedOrder = ref<ParkOrderCreateResponse | null>(null)
const trackedOrderId = ref<number | null>(null)
const mobileApiKey = ref('')
const orderMode = ref<MobileOrderMode>(loadMobileOrderMode())
const geoMapAvailable = isAmapConfigured()
const showApiKeySettings = import.meta.env.DEV
const trackingPanelRef = ref<InstanceType<typeof OrderTrackingPanel> | null>(null)
const quickOrderPanelRef = ref<InstanceType<typeof QuickOrderPanel> | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

function resolveDefaultMobileApiKey() {
  return (
    localStorage.getItem('fsd_mobile_api_key')?.trim() ||
    (import.meta.env.VITE_MOBILE_API_KEY as string | undefined)?.trim() ||
    ''
  )
}

function persistMobileApiKey() {
  const trimmed = mobileApiKey.value.trim()
  if (trimmed) localStorage.setItem('fsd_mobile_api_key', trimmed)
  else localStorage.removeItem('fsd_mobile_api_key')
}

const form = reactive<ParkOrderCreateRequest>({
  parkId: undefined,
  externalOrderNo: '',
  pickupStationId: undefined as unknown as number,
  dropoffStationId: undefined as unknown as number,
  routeId: undefined as number | undefined,
  priority: 'P1',
  remark: '',
})

const parkOptions = computed(() =>
  parks.value.map(park => ({
    value: park.parkId,
    label: park.parkName,
  })),
)

const isSinglePark = computed(() => parks.value.length <= 1)

const lockedParkName = computed(() => {
  const park = parks.value.find(item => item.parkId === form.parkId)
  return park?.parkName || '叠石桥 L1 试点'
})

const orderableStations = computed(() => orderableStationsForMode(stations.value, orderMode.value))

const activeDemoRoutes = computed(() =>
  orderMode.value === 'schematic' ? parkSchematicDemoRoutes : parkDeliveryDemoRoutes,
)

const orderableStationCount = computed(() => {
  if (orderableStations.value.length > 0) return orderableStations.value.length
  return orderMode.value === 'schematic' ? SCHEMATIC_ORDERABLE_STATION_COUNT : ZJF_ORDERABLE_STATION_COUNT
})

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

const trackingMapCenter = computed((): [number, number] => {
  if (trackedVehicle.value) return vehicleGeoPosition(trackedVehicle.value)
  if (trackedOrder.value) {
    const pickup = trackedOrder.value.pickupStation
    if (pickup.coordLng != null && pickup.coordLat != null) {
      return [Number(pickup.coordLng), Number(pickup.coordLat)]
    }
  }
  if (parkLayout.value?.centerLng != null && parkLayout.value?.centerLat != null) {
    return [Number(parkLayout.value.centerLng), Number(parkLayout.value.centerLat)]
  }
  return pilotMapCenter()
})

const trackingGeoMarkers = computed(() => {
  const markers = []
  if (trackedOrder.value) {
    markers.push(
      ...buildStationGeoMarkers([
        {
          id: 'pickup',
          station: trackedOrder.value.pickupStation,
          label: `取 ${trackedOrder.value.pickupStation.stationCode}`,
        },
        {
          id: 'dropoff',
          station: trackedOrder.value.dropoffStation,
          label: `送 ${trackedOrder.value.dropoffStation.stationCode}`,
        },
      ]),
    )
  }
  if (trackedVehicle.value) {
    markers.push(
      toAvGeoMarker(String(trackedVehicle.value.vehicleId), vehicleGeoPosition(trackedVehicle.value), {
        onlineStatus: trackedVehicle.value.onlineStatus,
        dispatchStatus: trackedVehicle.value.dispatchStatus,
        charging: trackedVehicle.value.charging,
        lowBattery: trackedVehicle.value.lowBattery,
        heading: trackedVehicle.value.heading ?? null,
        label: trackedVehicle.value.vehicleCode,
      }),
    )
  }
  return markers
})

const trackingGeoPolylines = computed(() => {
  const focusVehicle = trackedVehicle.value ? [trackedVehicle.value] : []
  return buildGeoPolylines(focusVehicle, trackedOrder.value ? [trackedOrder.value] : [], {
    includeOrderLines: false,
    focusVehicleId: trackedVehicle.value?.vehicleId ?? null,
    focusOrderId: trackedOrder.value?.orderId ?? null,
  })
})

const routeAnomalyText = computed(() => routeAnomalyWarning(modeVehicles.value))

const trackingGeoPolygons = computed(() => buildGeofencePolygons(parkGeofences.value))

const trackingFitViewPoints = computed((): [number, number][] => {
  if (!trackedVehicle.value) return []
  const points = collectRouteFitPoints([trackedVehicle.value], {
    focusVehicleId: trackedVehicle.value.vehicleId,
  })
  if (points.length >= 2) return points
  if (trackedOrder.value) {
    const pickup = trackedOrder.value.pickupStation
    const dropoff = trackedOrder.value.dropoffStation
    if (pickup.coordLng != null && dropoff.coordLng != null) {
      return [
        [Number(pickup.coordLng), Number(pickup.coordLat)],
        [Number(dropoff.coordLng), Number(dropoff.coordLat)],
      ]
    }
  }
  return points
})

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

const lastToastStage = ref<string | null>(null)

watch(
  () => trackedOrder.value?.runtimeStage,
  (stage, previous) => {
    if (!stage || stage === lastToastStage.value) return
    if (stage === 'COMPLETED' && previous !== 'COMPLETED') {
      message.success({ content: '货物已送达，无人车返回待命区', duration: 5 })
    }
    if (stage === 'HEADING_TO_DROPOFF' && previous === 'LOADING') {
      message.info('已取货，正在沿道路配送至送货点')
    }
    lastToastStage.value = stage
  },
)

function applyDefaultStations() {
  const synced = syncDefaultOrderStations(stations.value, orderMode.value, {
    pickupStationId: form.pickupStationId,
    dropoffStationId: form.dropoffStationId,
  })
  if (synced.pickupStationId) form.pickupStationId = synced.pickupStationId
  if (synced.dropoffStationId) form.dropoffStationId = synced.dropoffStationId
}

function resolveStationIds(pickupCode: string, dropoffCode: string) {
  const orderable = orderableStationsForMode(stations.value, orderMode.value)
  const pickup = findMobileOrderStation(stations.value, { stationCode: pickupCode }, orderable)
  const dropoff = findMobileOrderStation(stations.value, { stationCode: dropoffCode }, orderable)
  return { pickup, dropoff }
}

function ensureValidOrderStationIds(): boolean {
  const orderable = orderableStationsForMode(stations.value, orderMode.value)
  const pickup = findMobileOrderStation(stations.value, { stationId: form.pickupStationId }, orderable)
  const dropoff = findMobileOrderStation(stations.value, { stationId: form.dropoffStationId }, orderable)
  if (pickup && dropoff) return true

  applyDefaultStations()
  message.warning('站点列表已更新，请重新选择取送货点后再下单')
  return false
}

function handleOrderModeUpdate(mode: MobileOrderMode) {
  if (orderMode.value === mode) return
  orderMode.value = mode
  persistMobileOrderMode(mode)
  form.pickupStationId = undefined as unknown as number
  form.dropoffStationId = undefined as unknown as number
  form.routeId = undefined
  trackedOrderId.value = null
  applyDefaultStations()
}

async function submitDemoRoute(route: (typeof parkDeliveryDemoRoutes)[number] | (typeof parkSchematicDemoRoutes)[number]) {
  const { pickup, dropoff } = resolveStationIds(route.pickupCode, route.dropoffCode)
  if (!pickup || !dropoff) {
    message.warning('演示站点尚未加载，请稍后重试')
    return
  }
  form.pickupStationId = pickup.stationId
  form.dropoffStationId = dropoff.stationId
  await submitOrder()
}

async function fetchParks() {
  loadingParks.value = true
  try {
    const response = await listParks()
    parks.value = response.data || []
    if (!form.parkId) {
      const defaultPark = parks.value.find(park => park.defaultPark) || parks.value[0]
      if (defaultPark) form.parkId = defaultPark.parkId
    }
  } finally {
    loadingParks.value = false
  }
}

async function fetchStations() {
  if (!form.parkId) {
    stations.value = []
    return
  }
  loadingStations.value = true
  try {
    const response = await getParkStations(form.parkId)
    stations.value = response.data || []
    applyDefaultStations()
  } finally {
    loadingStations.value = false
  }
}

async function fetchLayout() {
  if (!form.parkId) return
  const response = await getParkLayout(form.parkId)
  parkLayout.value = response.data
}

async function fetchGeofences() {
  if (!form.parkId) {
    parkGeofences.value = []
    return
  }
  const response = await getParkGeofences(form.parkId)
  parkGeofences.value = response.data || []
}

async function handleParkIdUpdate(parkId: number) {
  if (form.parkId === parkId) return
  form.parkId = parkId
  form.pickupStationId = undefined as unknown as number
  form.dropoffStationId = undefined as unknown as number
  form.routeId = undefined
  await Promise.all([fetchStations(), fetchLayout(), fetchGeofences()])
}

async function fetchOrders() {
  try {
    const response = await getParkOrders({ silent: true })
    parkOrders.value = response.data || []
    if (trackedOrderId.value && !visibleParkOrders.value.some(order => order.orderId === trackedOrderId.value)) {
      trackedOrderId.value = null
    }
    if (!trackedOrderId.value && visibleParkOrders.value[0]) {
      trackedOrderId.value = visibleParkOrders.value[0].orderId
    }
  } catch {
    // 轮询失败时不打断下单
  }
}

async function fetchVehicles() {
  try {
    const response = await getParkVehicles({ silent: true })
    vehicles.value = response.data || []
  } catch {
    // 车辆列表失败时静默
  }
}

function validateForm() {
  if (!form.pickupStationId) {
    message.error('请选择取货站点')
    return false
  }
  if (!form.dropoffStationId) {
    message.error('请选择送货站点')
    return false
  }
  if (form.pickupStationId === form.dropoffStationId) {
    message.error('取货站点和送货站点不能相同')
    return false
  }
  return true
}

async function submitOrder() {
  if (!validateForm()) return
  if (!ensureValidOrderStationIds()) return
  submitting.value = true
  try {
    const response = await createParkOrder(
      {
        parkId: form.parkId,
        externalOrderNo: form.externalOrderNo?.trim() || undefined,
        pickupStationId: form.pickupStationId,
        dropoffStationId: form.dropoffStationId,
        routeId: form.routeId,
        priority: form.priority || 'P1',
        remark: form.remark?.trim() || undefined,
      },
      mobileApiKey.value,
    )
    lastCreatedOrder.value = response.data
    trackedOrderId.value = response.data.orderId
    message.success('订单已创建，手机端将自动开始追踪配送')
    form.externalOrderNo = ''
    form.remark = ''
    await Promise.all([fetchOrders(), fetchVehicles()])
    await nextTick()
    trackingPanelRef.value?.$el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (err: unknown) {
    const msg =
      (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
      (err instanceof Error ? err.message : '下单失败')
    message.error(msg.includes('X-Mobile-Api-Key') ? `${msg}（请配置 VITE_MOBILE_API_KEY）` : msg)
  } finally {
    submitting.value = false
  }
}

function scrollToQuickOrder() {
  quickOrderPanelRef.value?.$el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(async () => {
  mobileApiKey.value = resolveDefaultMobileApiKey()
  await fetchParks()
  await Promise.all([fetchStations(), fetchLayout(), fetchGeofences(), fetchOrders(), fetchVehicles()])
  pollTimer = setInterval(() => {
    fetchOrders()
    fetchVehicles()
  }, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped lang="less">
.mobile-order-page {
  min-height: 100vh;
  min-height: 100dvh;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  background:
    radial-gradient(circle at 12% 0%, rgba(0, 180, 216, 0.12), transparent 34%),
    radial-gradient(circle at 88% 8%, rgba(255, 183, 3, 0.08), transparent 28%),
    linear-gradient(180deg, #07111f 0%, #04080f 100%);
  color: #d8e4f2;
}

.mobile-header {
  position: sticky;
  top: 0;
  z-index: 20;
  padding:
    calc(12px + env(safe-area-inset-top, 0px))
    16px
    14px;
  border-bottom: 1px solid rgba(62, 166, 255, 0.1);
  background: rgba(4, 8, 16, 0.88);
  backdrop-filter: blur(16px);
}

.header-back {
  display: inline-block;
  margin-bottom: 10px;
  color: #58b6ff;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
}

.header-brand h1 {
  margin: 6px 0 0;
  font-size: 22px;
  line-height: 1.15;
  letter-spacing: -0.03em;
  color: #f4f8fc;
}

.header-eyebrow {
  color: #ffb703;
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.header-stats {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.header-stats span {
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(62, 166, 255, 0.08);
  border: 1px solid rgba(62, 166, 255, 0.14);
  font-size: 11px;
  font-weight: 700;
  color: #8fb4d9;
}

.mobile-main {
  width: min(100%, 560px);
  margin: 0 auto;
  padding:
    16px
    16px
    calc(24px + env(safe-area-inset-bottom, 0px));
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settings-panel {
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px dashed rgba(62, 166, 255, 0.18);
  background: rgba(6, 12, 22, 0.45);
  color: #6f88a2;
  font-size: 12px;

  summary {
    cursor: pointer;
    font-weight: 700;
    color: #8fb4d9;
    list-style: none;

    &::-webkit-details-marker {
      display: none;
    }
  }
}

.api-key-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;

  span {
    font-size: 11px;
    color: #6f88a2;
  }

  input {
    width: 100%;
    height: 40px;
    padding: 0 12px;
    border-radius: 12px;
    border: 1px solid rgba(62, 166, 255, 0.14);
    background: rgba(4, 8, 16, 0.72);
    color: #e8f2ff;
    font-size: 13px;
    outline: none;
  }
}

.api-key-note {
  margin: 8px 0 0;
  font-size: 11px;
  color: #5a7a9a;
}

@media (max-width: 420px) {
  .header-brand h1 {
    font-size: 20px;
  }
}
</style>

<style lang="less">
.ant-select-dropdown {
  background: rgba(8, 17, 29, 0.96) !important;
  border: 1px solid rgba(62, 166, 255, 0.14) !important;
  backdrop-filter: blur(12px);
}

.ant-select-item {
  color: #d8e4f2 !important;
}

.ant-select-item-option-active {
  background: rgba(62, 166, 255, 0.1) !important;
}

.ant-select-item-option-selected {
  background: rgba(62, 166, 255, 0.18) !important;
  color: #74c2ff !important;
}

.ant-select-item-group {
  color: #58b6ff !important;
  font-weight: 700;
}
</style>
