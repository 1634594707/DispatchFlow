<template>
  <div class="mobile-order-page">
    <header class="mobile-header">
      <div class="header-row">
        <div class="brand-seal" aria-hidden="true">找</div>
        <div class="header-brand">
          <span class="header-eyebrow">找家纺网 · 无人车配送</span>
          <h1>叫车送货</h1>
        </div>
      </div>
      <div class="header-stats">
        <span>{{ orderableStationCount }} 个服务点</span>
        <span>{{ activeOrders.length }} 单配送中</span>
        <span class="service-open">今日可下单</span>
      </div>
    </header>

    <main class="mobile-main">
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
        :order-priority="form.orderPriority || 'NORMAL'"
        :weight="form.weight"
        :remark="form.remark || ''"
        :loading-parks="loadingParks"
        :loading-stations="loadingStations"
        :park-options="parkOptions"
        @update:park-id="handleParkIdUpdate"
        @update:pickup-station-id="form.pickupStationId = $event"
        @update:dropoff-station-id="form.dropoffStationId = $event"
        @update:weight="form.weight = $event"
        @update:remark="form.remark = $event"
        @quick-fill="quickFillDefaults"
        @submit-demo="submitDemoRoute"
        @submit-custom="submitOrder"
      />

      <OrderTrackingPanel
        v-if="trackedOrder"
        ref="trackingPanelRef"
        :order="trackedOrder"
        :active-orders="activeOrders"
        :vehicle="trackedVehicle"
        :park-layout="parkLayout"
        :geo-map-available="geoMapAvailable"
        :force-schematic-map="false"
        :map-center="trackingMapCenter"
        :geo-markers="trackingGeoMarkers"
        :geo-polylines="trackingGeoPolylines"
        :geo-polygons="trackingGeoPolygons"
        :fit-view-points="trackingFitViewPoints"
        :route-anomaly-text="routeAnomalyText"
        :screen-link="trackingScreenLink"
        :remaining-label="remainingDeliveryLabel"
        :last-updated-label="trackingLastUpdatedLabel"
        :connection-stale="trackingConnectionStale"
        @select-order="trackedOrderId = $event"
        @order-again="scrollToQuickOrder"
      />
    </main>

    <MobileTabBar :active-order-count="activeOrders.length" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import OrderTrackingPanel from '@/components/mobile/OrderTrackingPanel.vue'
import QuickOrderPanel from '@/components/mobile/QuickOrderPanel.vue'
import MobileTabBar from '@/components/mobile/MobileTabBar.vue'
import { parkDeliveryDemoRoutes } from '@/constants/parkDelivery'
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
  findMobileOrderStation,
  orderableStationsForMode,
  syncDefaultOrderStations,
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
const trackedOrderId = ref<number | null>(null)
const mobileApiKey = ref('')
const orderMode = ref<MobileOrderMode>('geo')
const geoMapAvailable = isAmapConfigured()
const trackingPanelRef = ref<InstanceType<typeof OrderTrackingPanel> | null>(null)
const quickOrderPanelRef = ref<InstanceType<typeof QuickOrderPanel> | null>(null)
const route = useRoute()
const lastTrackingUpdatedAt = ref<Date | null>(null)
const trackingFailureCount = ref(0)
let pollTimer: ReturnType<typeof setTimeout> | null = null
let pollingStopped = false

function resolveDefaultMobileApiKey() {
  return (
    sessionStorage.getItem('fsd_mobile_api_key')?.trim() ||
    (import.meta.env.VITE_MOBILE_API_KEY as string | undefined)?.trim() ||
    ''
  )
}

const form = reactive<ParkOrderCreateRequest>({
  parkId: undefined,
  externalOrderNo: '',
  pickupStationId: undefined as unknown as number,
  dropoffStationId: undefined as unknown as number,
  routeId: undefined as number | undefined,
  priority: 'P1',
  orderPriority: 'NORMAL',
  weight: undefined as number | undefined,
  remark: '',
})

const parkOptions = computed(() =>
  parks.value.map((park) => ({
    value: park.parkId,
    label: park.parkName,
  })),
)

const isSinglePark = computed(() => parks.value.length <= 1)

const lockedParkName = computed(() => {
  const park = parks.value.find((item) => item.parkId === form.parkId)
  return park?.parkName || '叠石桥 L1 试点'
})

const orderableStations = computed(() => orderableStationsForMode(stations.value, orderMode.value))

const activeDemoRoutes = computed(() => parkDeliveryDemoRoutes)

const orderableStationCount = computed(() => {
  if (orderableStations.value.length > 0) return orderableStations.value.length
  return ZJF_ORDERABLE_STATION_COUNT
})

const visibleParkOrders = computed(() => filterGeoDeliveryOrders(parkOrders.value))

const activeOrders = computed(() =>
  visibleParkOrders.value.filter((order) => !['COMPLETED', 'FAILED'].includes(order.runtimeStage)),
)

const modeVehicles = computed(() => filterGeoDeliverySimVehicles(vehicles.value))

const trackedOrder = computed(() => {
  if (trackedOrderId.value) {
    const matched = visibleParkOrders.value.find((order) => order.orderId === trackedOrderId.value)
    if (matched) return matched
  }
  return activeOrders.value[0] || visibleParkOrders.value[0] || null
})

const trackedVehicle = computed(() => {
  if (!trackedOrder.value?.vehicleId) return null
  return (
    modeVehicles.value.find((vehicle) => vehicle.vehicleId === trackedOrder.value?.vehicleId) ||
    null
  )
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
      toAvGeoMarker(
        String(trackedVehicle.value.vehicleId),
        vehicleGeoPosition(trackedVehicle.value),
        {
          onlineStatus: trackedVehicle.value.onlineStatus,
          dispatchStatus: trackedVehicle.value.dispatchStatus,
          charging: trackedVehicle.value.charging,
          lowBattery: trackedVehicle.value.lowBattery,
          heading: trackedVehicle.value.heading ?? null,
          label: trackedVehicle.value.vehicleCode,
        },
      ),
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

const trackingLastUpdatedLabel = computed(() => {
  if (!lastTrackingUpdatedAt.value) return null
  return lastTrackingUpdatedAt.value.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
})

const trackingConnectionStale = computed(() => trackingFailureCount.value >= 3)

const remainingDeliveryLabel = computed(() => {
  const vehicle = trackedVehicle.value
  if (!vehicle || trackedOrder.value?.runtimeStage === 'COMPLETED') return null

  const planned = vehicle.plannedRouteGeo
  if (planned && planned.length >= 2) {
    const path = planned.map(
      (point) =>
        [Number(point.longitude ?? point.x), Number(point.latitude ?? point.y)] as [number, number],
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

/** 快速下单：一键填充默认取货点和送货点 */
function quickFillDefaults() {
  if (!stations.value.length) {
    message.warning('站点尚未加载，请稍后重试')
    return
  }
  applyDefaultStations()
  message.success('已填入默认取货点与送货点，可直接提交')
  scrollToQuickOrder()
}

function resolveStationIds(pickupCode: string, dropoffCode: string) {
  const orderable = orderableStationsForMode(stations.value, orderMode.value)
  const pickup = findMobileOrderStation(stations.value, { stationCode: pickupCode }, orderable)
  const dropoff = findMobileOrderStation(stations.value, { stationCode: dropoffCode }, orderable)
  return { pickup, dropoff }
}

function ensureValidOrderStationIds(): boolean {
  const orderable = orderableStationsForMode(stations.value, orderMode.value)
  const pickup = findMobileOrderStation(
    stations.value,
    { stationId: form.pickupStationId },
    orderable,
  )
  const dropoff = findMobileOrderStation(
    stations.value,
    { stationId: form.dropoffStationId },
    orderable,
  )
  if (pickup && dropoff) return true

  applyDefaultStations()
  message.warning('站点列表已更新，请重新选择取送货点后再下单')
  return false
}

async function submitDemoRoute(route: { pickupCode: string; dropoffCode: string }) {
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
      const defaultPark = parks.value.find((park) => park.defaultPark) || parks.value[0]
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
  const response = await getParkOrders({ silent: true })
  parkOrders.value = response.data || []
  if (
    trackedOrderId.value &&
    !visibleParkOrders.value.some((order) => order.orderId === trackedOrderId.value)
  ) {
    trackedOrderId.value = null
  }
  if (!trackedOrderId.value && visibleParkOrders.value[0]) {
    trackedOrderId.value = visibleParkOrders.value[0].orderId
  }
}

async function fetchVehicles() {
  const response = await getParkVehicles({ silent: true })
  vehicles.value = response.data || []
}

function scheduleTrackingRefresh() {
  if (pollingStopped) return
  const delay = Math.min(3000 * 2 ** trackingFailureCount.value, 30000)
  pollTimer = setTimeout(refreshTrackingSnapshot, delay)
}

async function refreshTrackingSnapshot() {
  try {
    await Promise.all([fetchOrders(), fetchVehicles()])
    trackingFailureCount.value = 0
    lastTrackingUpdatedAt.value = new Date()
  } catch {
    trackingFailureCount.value += 1
  } finally {
    scheduleTrackingRefresh()
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
        orderPriority: form.orderPriority || 'NORMAL',
        weight: form.weight,
        remark: form.remark?.trim() || undefined,
      },
      mobileApiKey.value,
    )
    trackedOrderId.value = response.data.orderId
    message.success('订单已创建，手机端将自动开始追踪配送')
    form.externalOrderNo = ''
    form.remark = ''
    void refreshTrackingSnapshot()
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
  await Promise.all([fetchStations(), fetchLayout(), fetchGeofences()])
  await refreshTrackingSnapshot()
  // 支持从订单页跳转时指定追踪订单
  const queryOrderId = route.query.orderId
  if (queryOrderId) {
    const orderId = Number(queryOrderId)
    if (orderId > 0) {
      trackedOrderId.value = orderId
      void nextTick(() =>
        trackingPanelRef.value?.$el.scrollIntoView({ behavior: 'smooth', block: 'start' }),
      )
    }
  }
})

onUnmounted(() => {
  pollingStopped = true
  if (pollTimer) clearTimeout(pollTimer)
})
</script>

<style scoped lang="less">
/* 浅色主题变量覆盖 —— 仅作用于本页面容器，子组件通过 var(--fsd-*) 自动继承浅色值。
   参考：顺丰 / 京东物流移动端，#1989fa 主色 + #f5f6fa 底色 + 卡片化白底。 */
.mobile-order-page {
  --fsd-bg-deep: #f5f6fa;
  --fsd-bg-base: #ffffff;
  --fsd-bg-elevated: #ffffff;
  --fsd-bg-hover: #f5f5f5;
  --fsd-bg-active: #e6f4ff;
  --fsd-bg-spotlight: #ffffff;

  --fsd-text-primary: #1a1a1a;
  --fsd-text-secondary: #666666;
  --fsd-text-tertiary: #999999;
  --fsd-text-heading: #1a1a1a;
  --fsd-text-muted: #cccccc;

  --fsd-border: rgba(0, 0, 0, 0.06);
  --fsd-border-active: rgba(0, 0, 0, 0.12);
  --fsd-border-split: rgba(0, 0, 0, 0.04);
  --fsd-border-strong: rgba(0, 0, 0, 0.18);

  --fsd-accent: #1989fa;
  --fsd-accent-strong: #096dd9;
  --fsd-accent-muted: #1989fa;
  --fsd-accent-deep: #0050b3;
  --fsd-accent-glow: rgba(25, 137, 250, 0.12);
  --fsd-accent-bg: rgba(25, 137, 250, 0.08);
  --fsd-accent-border: rgba(25, 137, 250, 0.4);
  --fsd-accent-subtle: rgba(25, 137, 250, 0.04);

  --fsd-success: #52c41a;
  --fsd-warning: #faad14;
  --fsd-error: #ff4d4f;
  --fsd-info: #1989fa;

  --fsd-shadow-card: 0 1px 4px rgba(0, 0, 0, 0.04);
  --fsd-shadow-elevated: 0 4px 16px rgba(0, 0, 0, 0.08);
  --fsd-shadow-soft: 0 1px 2px rgba(0, 0, 0, 0.04);

  /* 关键修复：使用固定 height 而非 min-height，让 overflow-y:auto 生效。
     原因：global.less 中 html/body/#app 均为 height:100% + overflow:hidden，
     若用 min-height:100vh，元素会被内容撑高并超出 #app，超出部分被裁剪，
     导致不能滚动。改为固定高度后，元素成为真正的滚动容器。 */
  height: 100vh;
  height: 100dvh;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
  touch-action: pan-y;
  background: #f5f6fa;
  color: #1a1a1a;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif;
}

.mobile-header {
  position: sticky;
  top: 0;
  z-index: var(--fsd-z-header);
  padding: calc(12px + env(safe-area-inset-top, 0px)) 18px 14px;
  border-bottom: 1px solid #f0f0f0;
  background: #ffffff;
}

.header-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-seal {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  border-radius: 8px;
  background: #1989fa;
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  box-shadow: 0 4px 12px rgba(25, 137, 250, 0.22);
}

.header-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
  padding: 4px 0;
  color: #666;
  font-size: 12px;
  font-weight: 500;
  text-decoration: none;
  transition: color var(--fsd-transition-fast);

  &:hover {
    color: #1989fa;
  }
}

.header-brand h1 {
  margin: 3px 0 0;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
  font-size: 22px;
  line-height: 1.2;
  letter-spacing: 0;
  color: #1a1a1a;
  font-weight: 600;
}

.header-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #6b7f92;
  font-size: 10px;
  letter-spacing: 0;
  font-weight: 600;

  &::before {
    display: none;
  }
}

.header-stats {
  display: flex;
  gap: 6px;
  margin-top: 14px;
  flex-wrap: wrap;
}

.header-stats span {
  padding: 5px 10px;
  border-radius: var(--fsd-radius-full);
  background: #f5f5f5;
  border: 1px solid #f0f0f0;
  font-size: 11px;
  font-weight: 600;
  color: #666;
  font-feature-settings: 'tnum';
  font-family: var(--fsd-font-mono);
}

.header-stats .service-open {
  background: #effaf4;
  border-color: #d8f3e3;
  color: #168a50;
}

.mobile-main {
  width: min(100%, var(--fsd-mobile-max-width));
  margin: 0 auto;
  padding: 12px 16px calc(80px + env(safe-area-inset-bottom, 0px));
  display: flex;
  flex-direction: column;
  gap: 14px;
}

@media (max-width: 420px) {
  .header-brand h1 {
    font-size: 20px;
  }
  .mobile-main {
    padding: 10px 12px calc(76px + env(safe-area-inset-bottom, 0px));
  }
}
</style>

<style lang="less">
/* 浅色主题：Ant Design 下拉/输入框全局覆盖（仅影响本页 .mobile-order-page 内的下拉，
   因为本页容器覆盖了 --fsd-* 变量，但 ant-select-dropdown 渲染在 body 下，
   需要单独使用浅色样式） */
.ant-select-dropdown {
  background: #ffffff !important;
  border: 1px solid #e8e8e8 !important;
  backdrop-filter: blur(16px);
  border-radius: 12px !important;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08) !important;
}

.ant-select-item {
  color: #333 !important;
  border-radius: 8px !important;
}

.ant-select-item-option-active {
  background: #f5f5f5 !important;
}

.ant-select-item-option-selected {
  background: #e6f4ff !important;
  color: #1989fa !important;
  font-weight: 600;
}

.ant-select-item-group {
  color: #1989fa !important;
  font-weight: 600;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}
</style>
