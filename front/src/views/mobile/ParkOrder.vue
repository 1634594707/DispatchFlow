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
        <span v-if="orderableStationCount !== null">{{ orderableStationCount }} 个服务点</span>
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
        :pickup-endpoint="pickupEndpoint"
        :dropoff-endpoint="dropoffEndpoint"
        :rejection="rejection"
        :priority="form.priority || 'P1'"
        :order-priority="form.orderPriority || 'NORMAL'"
        :weight="form.weight"
        :remark="form.remark || ''"
        :loading-parks="loadingParks"
        :loading-stations="loadingStations"
        :has-tracked-order="Boolean(trackedOrder)"
        :park-options="parkOptions"
        :map-center="orderMapCenter"
        @update:park-id="handleParkIdUpdate"
        @update:pickup-endpoint="pickupEndpoint = $event"
        @update:dropoff-endpoint="dropoffEndpoint = $event"
        @update:weight="form.weight = $event"
        @update:remark="form.remark = $event"
        @quick-fill="quickFillDefaults"
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
        :layer-summary="trackingLayerSummary"
        :vehicle-spec="PILOT_VEHICLE_SPEC"
        :fence-flash="serviceFenceFlash"
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
  aggregateMarkersByPosition,
  buildGeofencePolygons,
  buildGeoPolylines,
  buildStationGeoMarkers,
  buildVehicleGeoMarkers,
  collectRouteFitPoints,
  countVehiclesWithUnknownPosition,
  filterGeoDeliveryOrders,
  filterGeoDeliverySimVehicles,
  findMobileOrderStation,
  isAmapConfigured,
  MOBILE_SERVICE_FENCE_PREFIX,
  mobileEnergyFacilityStations,
  orderableStationsForMode,
  pilotMapCenter,
  syncDefaultOrderStations,
  vehicleGeoPosition,
} from '@/maps'
import { formatDeliveryEta, formatDistance, polylineLengthMeters } from '@/maps/geoDistance'
import { buildGeoTrackingLink } from '@/constants/parkDelivery'
import { PILOT_VEHICLE_SPEC } from '@/constants/vehicleSpec'
import {
  describeOrderRejection,
  endpointPayload,
  isCompleteEndpoint,
  needsServiceAreaGuidance,
} from '@/constants/orderEndpoints'
import type { OrderRejection } from '@/constants/orderEndpoints'
import { routeAnomalyWarning } from '@/maps/routeValidation'
import type {
  ParkGeofence,
  ParkLayout,
  ParkOrderCreateRequest,
  ParkOrderEndpoint,
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
/** 拒单引导用的"围栏描边闪一次"开关（§4 T2-f）：只有样式，不改几何。 */
const serviceFenceFlash = ref(false)
let pollTimer: ReturnType<typeof setTimeout> | null = null
let fenceFlashTimer: ReturnType<typeof setTimeout> | null = null
let pollingStopped = false

/**
 * 位置轮询基准间隔（§4 T2-e）。原值是散在表达式里的字面量 3000；改成 1500 ms 让追踪页的跳变
 * 更连贯（演示节奏，见路线图 §2 T0-c）。失败退避仍是 `base × 2^失败次数`，封顶 30 s 不动。
 * 代价：`/admin/park/*` 匿名路径不过限流 ⇒ 下单页静置时 QPS×2，演示时长内可接受，
 * 上生产常开要先看网关日志。
 */
const TRACKING_POLL_BASE_MS = 1500
/** 描边高亮的持续时间：一个"闪一下"的量级，不做循环动画。 */
const FENCE_FLASH_MS = 900

function resolveDefaultMobileApiKey() {
  return (
    sessionStorage.getItem('fsd_mobile_api_key')?.trim() ||
    (import.meta.env.VITE_MOBILE_API_KEY as string | undefined)?.trim() ||
    ''
  )
}

const form = reactive<Omit<ParkOrderCreateRequest, 'pickupStationId' | 'dropoffStationId'>>({
  idempotencyKey: createIdempotencyKey(),
  parkId: undefined,
  externalOrderNo: '',
  routeId: undefined,
  priority: 'P1',
  orderPriority: 'NORMAL',
  weight: undefined,
  remark: '',
})

/** 端点：站点与地图坐标二选一，互斥由 OrderEndpointInput 在切换时清空来保证。 */
const pickupEndpoint = ref<ParkOrderEndpoint | null>(null)
const dropoffEndpoint = ref<ParkOrderEndpoint | null>(null)
const rejection = ref<OrderRejection | null>(null)

/** 幂等键：每个下单意图一个，仅在下单成功后换新键。 */
function createIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return 'mob-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10)
}

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

/** 点选地图的初始视野：园区中心，缺省回落到试点常量。 */
const orderMapCenter = computed<[number, number]>(() => {
  const layout = parkLayout.value
  if (layout?.centerLng != null && layout?.centerLat != null) {
    return [Number(layout.centerLng), Number(layout.centerLat)]
  }
  return pilotMapCenter()
})

// 站点数只在真拿到数据后显示：写死的 8 是设施形态 v2 之前的口径（那时确实有 8 个可下单 ZJF 站），
// 现在生产可下单的是 1 座总仓库，列表没回来前宁可什么都不显示，也不报一个假数。
const orderableStationCount = computed<number | null>(() =>
  orderableStations.value.length > 0 ? orderableStations.value.length : null,
)

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
  const vehiclePosition = trackedVehicle.value ? vehicleGeoPosition(trackedVehicle.value) : null
  if (vehiclePosition) return vehiclePosition
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

/**
 * 追踪地图图层：补能设施（换电柜/充电桩）→ 本单取送点 → 全部在场车辆，被指派车高亮。
 *
 * <p>为什么以前只有一台车：全量车辆数据一直在拉（`fetchVehicles` → `getParkVehicles`），
 * 只是渲染层写了 `trackedVehicle ? [它] : []`。演示要讲"车队在跑"，所以整支 ZJF-AV-* 都得画出来。
 *
 * <p>⚠ 坐标只消费接口现成返回值（`vehicleGeoPosition`：没有真经纬度就返回 null）。SIM 行是像素坐标、
 * 真车行是 GCJ-02，这是逐行契约（§7.5），前端不许换算、不许兜底；拿不到坐标的车不画点，
 * 但必须计入 `trackingLayerSummary.positionUnknown`，不能安静消失。
 */
const trackingVehicleMarkers = computed(() =>
  buildVehicleGeoMarkers(modeVehicles.value, { selectedId: trackedVehicle.value?.vehicleId ?? null }),
)

/** 补能设施图层（§4 T2-c）：35 个 `FSD-SWAP-*` + 6 根 `FSD-CHG-*`，只在追踪地图上画。
 *  ⚠ 它们**永远不是货的起终点**：下单下拉走 `filterMobileOrderStations()`，与本图层无交集。 */
const facilityStations = computed(() => mobileEnergyFacilityStations(stations.value))

const trackingFacilityMarkers = computed(() =>
  aggregateMarkersByPosition(
    buildStationGeoMarkers(
      facilityStations.value.map((station) => ({
        station,
        id: `station-${station.stationId}`,
        label: `${station.stationName} · ${station.stationCode}`,
      })),
    ),
  ),
)

const trackingGeoMarkers = computed(() => {
  const markers = [...trackingFacilityMarkers.value]
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
  markers.push(...trackingVehicleMarkers.value)
  return markers
})

/** 图层小结：给地图下方那行读数用，也是 e2e 数 marker 的钩子（marker 在高德 canvas 里，DOM 数不到）。 */
const trackingLayerSummary = computed(() => ({
  vehicles: trackingVehicleMarkers.value.length,
  positionUnknown: countVehiclesWithUnknownPosition(modeVehicles.value),
  swap: trackingFacilityMarkers.value.filter((marker) => marker.markerType === 'swap').length,
  charging: trackingFacilityMarkers.value.filter((marker) => marker.markerType === 'charging').length,
  facilityPoints: trackingFacilityMarkers.value.length,
}))

const trackingGeoPolylines = computed(() => {
  const focusVehicle = trackedVehicle.value ? [trackedVehicle.value] : []
  return buildGeoPolylines(focusVehicle, trackedOrder.value ? [trackedOrder.value] : [], {
    // 追踪页只显示被追这一单的 OD 连线（`focusOrderId` 在下面锁死），其余单的线不进这张图。
    includeOrderLines: true,
    focusVehicleId: trackedVehicle.value?.vehicleId ?? null,
    focusOrderId: trackedOrder.value?.orderId ?? null,
  })
})

const routeAnomalyText = computed(() => routeAnomalyWarning(modeVehicles.value))

/**
 * 移动端只画受理围栏 `ZJF-ZONE-*`（§4 T2-b）：展示包络 `DEFAULT-BOUNDARY` 不参与受理，
 * 同屏两条边界会被读成"两条服务范围"。PC 工作台/大屏跟车仍按默认全量画，过滤只发生在这一处。
 */
const trackingGeoPolygons = computed(() =>
  buildGeofencePolygons(parkGeofences.value, {
    fenceCodePrefix: MOBILE_SERVICE_FENCE_PREFIX,
    flashOutline: serviceFenceFlash.value,
  }),
)

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

function stationIdOf(endpoint: ParkOrderEndpoint | null): number | undefined {
  return endpoint?.kind === 'station' ? endpoint.stationId : undefined
}

/**
 * 回填默认可下单站点。已经点了坐标的那一端不能被站点覆盖 —— 用户选的是"送到我这个位置"，
 * 静默换成服务点就是路线图 §7 明令禁止的那种兜底。
 *
 * ⚠ **两端各自独立回填**：设施形态 v2 之后货的起点只有总仓库一座，**库里已经没有"送货服务点"了**
 * （终点一律是用户选的坐标）。原来这里是"两端都算出来才回填"，于是总仓库也一起不填 ——
 * 页面表现为"取货点空着、推荐线路点了没反应"。
 */
function applyDefaultStations() {
  const synced = syncDefaultOrderStations(stations.value, orderMode.value, {
    pickupStationId: stationIdOf(pickupEndpoint.value),
    dropoffStationId: stationIdOf(dropoffEndpoint.value),
  })
  if (synced.pickupStationId && (pickupEndpoint.value == null || pickupEndpoint.value.kind === 'station')) {
    pickupEndpoint.value = { kind: 'station', stationId: synced.pickupStationId }
  }
  if (synced.dropoffStationId && (dropoffEndpoint.value == null || dropoffEndpoint.value.kind === 'station')) {
    dropoffEndpoint.value = { kind: 'station', stationId: synced.dropoffStationId }
  }
}

/**
 * 一键填回默认取货点。⚠ 不再宣称"送货点也填好了"—— 设施 v2 之后没有可下单的送货服务点，
 * 送货端必须由用户在地图上点（或手输坐标），所以没填上时要**明说还缺什么**。
 */
function quickFillDefaults() {
  if (!stations.value.length) {
    message.warning('站点尚未加载，请稍后重试')
    return
  }
  pickupEndpoint.value = null
  dropoffEndpoint.value = null
  applyDefaultStations()
  if (dropoffEndpoint.value == null) {
    message.info('取货点已设为发货仓库，请在地图上点一个送货位置')
  } else {
    message.success('已填入默认取货点与送货点，可直接提交')
  }
  scrollToQuickOrder()
}

/** 站点端要仍然在可下单列表里；坐标端由后端判据负责，前端不重复判。 */
function ensureValidOrderStations(): boolean {
  const orderable = orderableStationsForMode(stations.value, orderMode.value)
  for (const endpoint of [pickupEndpoint.value, dropoffEndpoint.value]) {
    if (endpoint?.kind !== 'station') continue
    if (!findMobileOrderStation(stations.value, { stationId: endpoint.stationId }, orderable)) {
      message.warning('服务点列表已更新，请重新选择取送货点后再下单')
      return false
    }
  }
  return true
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
  pickupEndpoint.value = null
  dropoffEndpoint.value = null
  rejection.value = null
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
  const response = await getParkVehicles({ silent: true, parkId: form.parkId })
  vehicles.value = response.data || []
}

function scheduleTrackingRefresh() {
  if (pollingStopped) return
  const delay = Math.min(TRACKING_POLL_BASE_MS * 2 ** trackingFailureCount.value, 30000)
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
  rejection.value = null
  if (!isCompleteEndpoint(pickupEndpoint.value)) {
    message.error('请选一个取货位置：挑服务点，或在地图上点一个坐标')
    return false
  }
  if (!isCompleteEndpoint(dropoffEndpoint.value)) {
    message.error('请选一个送货位置：挑服务点，或在地图上点一个坐标')
    return false
  }
  if (
    pickupEndpoint.value?.kind === 'station' &&
    dropoffEndpoint.value?.kind === 'station' &&
    pickupEndpoint.value.stationId === dropoffEndpoint.value.stationId
  ) {
    message.error('取货点和送货点不能相同')
    return false
  }
  return true
}

function buildOrderPayload(): ParkOrderCreateRequest {
  return {
    idempotencyKey: form.idempotencyKey,
    parkId: form.parkId,
    externalOrderNo: form.externalOrderNo?.trim() || undefined,
    ...endpointPayload('pickup', pickupEndpoint.value),
    ...endpointPayload('dropoff', dropoffEndpoint.value),
    routeId: form.routeId,
    priority: form.priority || 'P1',
    orderPriority: form.orderPriority || 'NORMAL',
    weight: form.weight,
    remark: form.remark?.trim() || undefined,
  }
}

async function submitOrder() {
  // 防重复提交（路线图 3.2/8.2）：处理中直接忽略后续触发
  if (submitting.value) return
  if (!validateForm()) return
  if (!ensureValidOrderStations()) return
  submitting.value = true
  const payload = buildOrderPayload()
  try {
    const response = await createParkOrder(payload, mobileApiKey.value)
    rejection.value = null
    trackedOrderId.value = response.data.orderId
    if (response.data.replayed) {
      message.success('检测到重复提交：已为您返回原订单，不会重复占用车辆')
    } else {
      message.success('订单已创建，手机端将自动开始追踪配送')
    }
    form.idempotencyKey = createIdempotencyKey()
    form.externalOrderNo = ''
    form.remark = ''
    void refreshTrackingSnapshot()
    await nextTick()
    trackingPanelRef.value?.$el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (err: unknown) {
    // 拒单原因常驻显示（toast 会飘走）：范围外/吸附不到/连不通是三件不同的事，必须让用户看见是哪件。
    const described = describeOrderRejection(err)
    if (described.detail.includes('X-Mobile-Api-Key')) {
      rejection.value = { ...described, detail: `${described.detail}（请配置 VITE_MOBILE_API_KEY）` }
    } else {
      rejection.value = described
    }
    // "点错了"这两类才引导：告诉用户下一步是重选一个点，并把围栏描边提亮一次（仅样式）。
    if (needsServiceAreaGuidance(described.code)) flashServiceFence()
  } finally {
    submitting.value = false
  }
}

/**
 * 服务范围描边闪一次（§4 T2-f）：只闪受理围栏，样式来自 `buildGeofencePolygons` 的 `flashOutline`。
 * 一次性 —— 定时器到点就落回常态，不做循环动画，免得变成常驻噪声。
 */
function flashServiceFence() {
  serviceFenceFlash.value = true
  if (fenceFlashTimer) clearTimeout(fenceFlashTimer)
  fenceFlashTimer = setTimeout(() => {
    serviceFenceFlash.value = false
    fenceFlashTimer = null
  }, FENCE_FLASH_MS)
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
  if (fenceFlashTimer) clearTimeout(fenceFlashTimer)
})
</script>

<style scoped lang="less">
/* Light mobile surface overrides. They retain the shared operational semantics. */
.mobile-order-page {
  --fsd-bg-deep: #f5f7f8;
  --fsd-bg-base: #ffffff;
  --fsd-bg-elevated: #ffffff;
  --fsd-bg-hover: #edf2f3;
  --fsd-bg-active: rgba(86, 185, 200, 0.16);
  --fsd-bg-spotlight: #ffffff;

  --fsd-text-primary: #1a1a1a;
  --fsd-text-secondary: #666666;
  --fsd-text-tertiary: #999999;
  --fsd-text-heading: #1a1a1a;
  --fsd-text-muted: #cccccc;

  --fsd-border: #dbe1e5;
  --fsd-border-active: #8baeb4;
  --fsd-border-split: #e7ecef;
  --fsd-border-strong: #b9c6cb;

  --fsd-accent: #438f9b;
  --fsd-accent-strong: #326f78;
  --fsd-accent-muted: #438f9b;
  --fsd-accent-deep: #285c64;
  --fsd-accent-selected: rgba(86, 185, 200, 0.16);
  --fsd-accent-bg: var(--fsd-accent-selected);
  --fsd-accent-border: rgba(67, 143, 155, 0.45);
  --fsd-accent-subtle: rgba(86, 185, 200, 0.08);
  --fsd-action-primary: #7fd1dd;
  --fsd-action-primary-hover: #91d9e2;
  --fsd-action-primary-active: #68c4d1;
  --fsd-text-on-action: #071014;

  --fsd-success: #4a9a75;
  --fsd-warning: #c29440;
  --fsd-error: #c45868;
  --fsd-info: #5f6c76;

  --fsd-shadow-card: none;
  --fsd-shadow-elevated: none;
  --fsd-shadow-soft: none;

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
  background: var(--fsd-bg-deep);
  color: var(--fsd-text-primary);
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif;
}

.mobile-header {
  position: sticky;
  top: 0;
  z-index: var(--fsd-z-header);
  padding: calc(var(--fsd-space-3) + env(safe-area-inset-top, 0px)) var(--fsd-space-4)
    var(--fsd-space-3);
  border-bottom: 1px solid var(--fsd-border);
  background: var(--fsd-bg-base);
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
  border-radius: var(--fsd-radius-sm);
  background: #e9eef0;
  color: var(--fsd-text-primary);
  font-size: 20px;
  font-weight: var(--fsd-font-semibold);
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
    color: var(--fsd-accent-strong);
  }
}

.header-brand h1 {
  margin: 3px 0 0;
  color: var(--fsd-text-primary);
  font-family: var(--fsd-font-sans);
  font-size: var(--fsd-text-xl);
  font-weight: var(--fsd-font-semibold);
  letter-spacing: var(--fsd-tracking-tight);
  line-height: var(--fsd-leading-tight);
}

.header-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: var(--fsd-space-1);
  color: var(--fsd-text-secondary);
  font-size: 10px;
  letter-spacing: 0;
  font-weight: var(--fsd-font-medium);

  &::before {
    display: none;
  }
}

.header-stats {
  display: flex;
  flex-wrap: wrap;
  gap: var(--fsd-space-2);
  margin-top: var(--fsd-space-3);
}

.header-stats span {
  padding: 4px 8px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-neutral-bg);
  color: var(--fsd-text-secondary);
  font-size: 11px;
  font-weight: var(--fsd-font-medium);
  font-feature-settings: 'tnum';
  font-family: var(--fsd-font-mono);
}

.header-stats .service-open {
  border-color: rgba(74, 154, 117, 0.3);
  background: rgba(74, 154, 117, 0.1);
  color: var(--fsd-success);
}

.mobile-main {
  display: flex;
  flex-direction: column;
  gap: 28px;
  width: min(100%, var(--fsd-mobile-max-width));
  margin: 0 auto;
  padding: var(--fsd-space-4) var(--fsd-space-4) calc(96px + env(safe-area-inset-bottom, 0px));
}

@media (max-width: 420px) {
  .header-brand h1 {
    font-size: 20px;
  }
}
</style>

<style lang="less">
/* Popup classes are attached by QuickOrderPanel so this skin cannot leak site-wide. */
.mobile-order-select-dropdown {
  border: 1px solid #dbe1e5 !important;
  border-radius: 10px !important;
  background: #ffffff !important;
  box-shadow: 0 16px 36px rgba(0, 0, 0, 0.28) !important;
}

.mobile-order-select-dropdown .ant-select-item {
  border-radius: 6px !important;
  color: #172027 !important;
}

.mobile-order-select-dropdown .ant-select-item-option-active {
  background: #edf2f3 !important;
}

.mobile-order-select-dropdown .ant-select-item-option-selected {
  background: rgba(86, 185, 200, 0.16) !important;
  color: #326f78 !important;
  font-weight: 600;
}

.mobile-order-select-dropdown .ant-select-item-group {
  color: #5f6c76 !important;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
</style>
