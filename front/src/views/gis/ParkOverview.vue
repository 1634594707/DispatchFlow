<template>
  <div class="park-overview-page">
    <AmapGeoMap
      v-if="geoMapAvailable"
      v-model:map-level="mapLevel"
      class="overview-map"
      :center="mapCenter"
      :markers="geoMarkers"
      :polygons="geoPolygons"
      :polylines="geoPolylines"
      :circles="geoCircles"
      @marker-click="selectMapMarker"
    />
    <div v-if="geoMapAvailable && routeWarning" class="route-anomaly-banner">
      {{ routeWarning }}
    </div>
    <div v-else class="overview-fallback">
      <p>高德 Key 未配置，已回退为列表视图。</p>
      <p class="hint">配置 <code>front/.env.local</code> 后可查看 L1 试点地图与道路轨迹。</p>
    </div>

    <div v-if="geoMapAvailable" class="map-status-bar">
      <span>GCJ-02</span>
      <span>L1 核心分区 {{ coreGeofences.length }}</span>
      <span>站点 {{ operationalStations.length }}</span>
      <span>{{ mapUpdatedLabel }}</span>
    </div>

    <aside class="overview-panel">
      <div class="panel-heading">
        <div>
          <h1>找家纺试点总览</h1>
          <p class="subtitle">{{ parkSubtitle }}</p>
        </div>
        <span class="live-indicator"><i />实时</span>
      </div>
      <div class="fleet-stats">
        <span>全网 {{ ZJF_FLEET_STATS.fleetSize }} 辆</span>
        <span>试点 {{ geoVehicles.length }} 辆</span>
      </div>
      <div class="map-legend" aria-label="地图图例">
        <span><i class="legend-dot pickup" />取货</span>
        <span><i class="legend-dot dropoff" />送货</span>
        <span><i class="legend-dot charging" />充电</span>
        <span><i class="legend-dot idle" />待命</span>
      </div>
      <section v-if="selectedStation" class="selection-card">
        <div class="selection-title">
          <span class="selection-kicker">{{ selectedStationRoleLabel }}</span>
          <strong>{{ selectedStation.stationName }}</strong>
        </div>
        <dl>
          <div>
            <dt>站点</dt>
            <dd>{{ selectedStation.stationCode }}</dd>
          </div>
          <div>
            <dt>下单能力</dt>
            <dd>{{ selectedStationDispatchLabel }}</dd>
          </div>
          <div>
            <dt>坐标</dt>
            <dd>服务位 / GCJ-02</dd>
          </div>
        </dl>
      </section>
      <section v-else-if="selectedVehicle" class="selection-card">
        <div class="selection-title">
          <span class="selection-kicker">车辆</span>
          <strong>{{ selectedVehicle.vehicleCode }}</strong>
        </div>
        <dl>
          <div>
            <dt>状态</dt>
            <dd>{{ selectedVehicle.runtimeStage }}</dd>
          </div>
          <div>
            <dt>路线来源</dt>
            <dd>{{ selectedVehicle.routeSource || '暂无执行路线' }}</dd>
          </div>
          <div>
            <dt>最后上报</dt>
            <dd :class="{ danger: selectedVehicle.telemetryStale }">
              {{ selectedVehicleTelemetryLabel }}
            </dd>
          </div>
        </dl>
      </section>
      <a-spin :spinning="loading">
        <div v-for="park in overview" :key="park.parkId" class="park-card">
          <div class="park-card-head">
            <strong>{{ park.parkName }}</strong>
            <span class="park-code">{{ park.parkCode }}</span>
          </div>
          <div class="park-stats">
            <span>{{ park.vehicleCount }} 车</span>
            <span>{{ park.onlineCount }} 在线</span>
            <span>{{ park.busyCount }} 执行</span>
          </div>
        </div>
      </a-spin>
      <div class="panel-links">
        <router-link class="tracking-link" to="/vehicle-tracking">进入监控大屏 →</router-link>
        <router-link class="tracking-link secondary" to="/mobile/order">移动下单 →</router-link>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import AmapGeoMap from '@/components/map/AmapGeoMap.vue'
import {
  getParkGeofences,
  getParkOverview,
  getParkOrders,
  getParkStations,
  getParkVehicles,
} from '@/api/park'
import {
  buildGeofencePolygons,
  buildGeoPolylines,
  buildL0CoverageCircles,
  buildOperationalStationMarkers,
  buildVehicleGeoMarkers,
  filterWorkbenchSituationStations,
  isAmapConfigured,
  pilotMapCenter,
  ZJF_FLEET_STATS,
  ZJF_PILOT_GEO,
} from '@/maps'
import { routeAnomalyWarning } from '@/maps/routeValidation'
import { filterGeoDeliverySimVehicles, workbenchStationRole } from '@/maps/stationLayers'
import { useParkMetadata } from '@/composables/useParkMetadata'
import type { GeoMapMarker } from '@/maps'
import type {
  ParkGeofence,
  ParkOrderSnapshot,
  ParkOverviewItem,
  ParkStation,
  ParkVehicleSnapshot,
} from '@/types/park'

type MapLevel = 'L0' | 'L1' | 'L2'

const loading = ref(false)
const overview = ref<ParkOverviewItem[]>([])
const vehicles = ref<ParkVehicleSnapshot[]>([])
const parkOrders = ref<ParkOrderSnapshot[]>([])
const parkGeofences = ref<ParkGeofence[]>([])
const stations = ref<ParkStation[]>([])
const selectedMarkerId = ref<string | null>(null)
const mapUpdatedAt = ref<Date | null>(null)
const geoMapAvailable = isAmapConfigured()
const mapLevel = ref<MapLevel>('L1')
const { metadata: parkMetadata, anchor: parkAnchor } = useParkMetadata()
let pollTimer: ReturnType<typeof setInterval> | null = null

const mapCenter = computed((): [number, number] => {
  // 阶段七 7.3：优先使用后端元数据锚点，其次园区列表 center，最后回退 ZJF_PILOT_GEO
  const metaAnchor = parkAnchor()
  if (metaAnchor) return metaAnchor
  const first = overview.value.find((park) => park.centerLng != null && park.centerLat != null)
  if (first?.centerLng != null && first?.centerLat != null) {
    return [Number(first.centerLng), Number(first.centerLat)]
  }
  return pilotMapCenter()
})

const parkSubtitle = computed(() => {
  const m = parkMetadata.value
  if (m?.parkName) {
    const sizePart =
      m.parkWidthMeters != null && m.parkHeightMeters != null
        ? ` · L1 ${m.parkWidthMeters}m×${m.parkHeightMeters}m`
        : ''
    return `${m.parkName}${sizePart}`
  }
  return `${ZJF_PILOT_GEO.label} · L1 ${ZJF_PILOT_GEO.parkWidthMeters}m×${ZJF_PILOT_GEO.parkHeightMeters}m`
})

const geoVehicles = computed(() => filterGeoDeliverySimVehicles(vehicles.value))

const operationalStations = computed(() =>
  filterWorkbenchSituationStations(stations.value, {
    showCharging: true,
    showIdle: true,
  }),
)

const selectedVehicleId = computed(() => {
  if (!selectedMarkerId.value || selectedMarkerId.value.startsWith('station-')) return null
  const id = Number(selectedMarkerId.value)
  return Number.isFinite(id) ? id : null
})

const geoMarkers = computed(() => [
  ...buildVehicleGeoMarkers(geoVehicles.value, { selectedId: selectedVehicleId.value }),
  ...buildOperationalStationMarkers(operationalStations.value, {
    selectedId: selectedMarkerId.value,
  }),
])

const coreGeofences = computed(() =>
  parkGeofences.value.filter(
    (fence) =>
      fence.scopeCode === 'L1_CORE' ||
      (!fence.scopeCode && fence.fenceCode.startsWith('ZJF-ZONE-')) ||
      fence.scopeCode === 'SAFETY_RESTRICTED',
  ),
)

const geoPolygons = computed(() => buildGeofencePolygons(coreGeofences.value))

const geoPolylines = computed(() =>
  buildGeoPolylines(geoVehicles.value, parkOrders.value, {
    includeOrderLines: false,
  }),
)

const geoCircles = computed(() => buildL0CoverageCircles())

const routeWarning = computed(() => routeAnomalyWarning(geoVehicles.value))

const selectedStation = computed(() => {
  if (!selectedMarkerId.value?.startsWith('station-')) return null
  const stationId = Number(selectedMarkerId.value.slice('station-'.length))
  return stations.value.find((station) => station.stationId === stationId) ?? null
})

const selectedVehicle = computed(
  () => geoVehicles.value.find((vehicle) => vehicle.vehicleId === selectedVehicleId.value) ?? null,
)

const selectedStationRoleLabel = computed(() => {
  if (!selectedStation.value) return ''
  return (
    {
      pickup: '取货服务位',
      dropoff: '送货服务位',
      express: '快递接驳',
      charging: '充电中心',
      idle: '车辆待命',
    } as const
  )[workbenchStationRole(selectedStation.value)]
})

const selectedStationDispatchLabel = computed(() => {
  if (!selectedStation.value) return '-'
  const role = workbenchStationRole(selectedStation.value)
  if (role === 'charging') return '仅补能调度'
  if (role === 'idle') return '不参与下单'
  return 'L1 核心区可选'
})

const selectedVehicleTelemetryLabel = computed(() => {
  const value = selectedVehicle.value?.lastTelemetryAt
  if (!value) return '无上报时间'
  const time = new Date(value).toLocaleTimeString('zh-CN', { hour12: false })
  return selectedVehicle.value?.telemetryStale ? `${time} · 数据陈旧` : time
})

const mapUpdatedLabel = computed(() =>
  mapUpdatedAt.value
    ? `更新 ${mapUpdatedAt.value.toLocaleTimeString('zh-CN', { hour12: false })}`
    : '等待数据',
)

function selectMapMarker(marker: GeoMapMarker) {
  selectedMarkerId.value = marker.id
}

async function refreshOverviewPanel() {
  const response = await getParkOverview()
  overview.value = response.data || []
}

async function refreshMapData() {
  const [vehicleRes, orderRes, fenceRes, stationRes] = await Promise.all([
    getParkVehicles(),
    getParkOrders(),
    getParkGeofences(),
    getParkStations(),
  ])
  vehicles.value = vehicleRes.data || []
  parkOrders.value = orderRes.data || []
  parkGeofences.value = fenceRes.data || []
  stations.value = stationRes.data || []
  mapUpdatedAt.value = new Date()
  if (!selectedMarkerId.value) {
    const activeOrder = parkOrders.value.find(
      (order) => !['COMPLETED', 'FAILED'].includes(order.runtimeStage),
    )
    const defaultStation =
      activeOrder?.dropoffStation ??
      operationalStations.value.find((station) => workbenchStationRole(station) === 'charging') ??
      operationalStations.value[0]
    if (defaultStation) selectedMarkerId.value = `station-${defaultStation.stationId}`
  }
}

async function refreshAll() {
  await Promise.all([refreshOverviewPanel(), refreshMapData()])
}

onMounted(async () => {
  loading.value = true
  try {
    await refreshAll()
  } finally {
    loading.value = false
  }
  pollTimer = setInterval(() => {
    void refreshAll()
  }, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped lang="less">
.park-overview-page {
  position: relative;
  width: 100%;
  height: calc(100vh - 64px);
  min-height: 520px;
  background: var(--fsd-bg-deep);
}

.overview-map {
  width: 100%;
  height: 100%;

  :deep(.amap-geo-map__level-switcher) {
    right: auto;
    left: 16px;
  }
}

.route-anomaly-banner {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 20;
  padding: 8px 16px;
  border-radius: 6px;
  background: rgba(255, 77, 109, 0.92);
  color: #fff;
  font-size: 13px;
  pointer-events: none;
  max-width: min(92vw, 520px);
  text-align: center;
}

.overview-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: rgba(255, 255, 255, 0.72);

  .hint {
    font-size: 12px;
    color: rgba(255, 255, 255, 0.45);
  }

  code {
    color: var(--fsd-success);
  }
}

.overview-panel {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 30;
  width: 300px;
  padding: 16px;
  border-radius: 8px;
  background: rgba(11, 16, 24, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: #fff;

  h1 {
    margin: 0;
    font-size: 18px;
    letter-spacing: 0;
  }

  .subtitle {
    margin: 4px 0 8px;
    font-size: 12px;
    color: rgba(255, 255, 255, 0.55);
  }
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.live-indicator {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: 0 0 auto;
  color: #8ce9bc;
  font-size: 11px;

  i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #2de08a;
    box-shadow: 0 0 0 3px rgba(45, 224, 138, 0.14);
  }
}

.fleet-stats {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  font-size: 11px;
  color: rgba(100, 149, 237, 0.9);
}

.map-legend {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  padding: 9px 0;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);

  span {
    display: flex;
    align-items: center;
    gap: 4px;
    min-width: 0;
    color: rgba(255, 255, 255, 0.62);
    font-size: 10px;
    white-space: nowrap;
  }
}

.legend-dot {
  width: 7px;
  height: 7px;
  border-radius: 2px;

  &.pickup {
    background: #00d4a8;
  }
  &.dropoff {
    background: #3b82f6;
  }
  &.charging {
    background: #a66cff;
  }
  &.idle {
    background: #94a3b8;
  }
}

.selection-card {
  margin: 12px 0 4px;
  padding: 11px;
  border: 1px solid rgba(34, 199, 230, 0.24);
  border-left: 3px solid #22c7e6;
  border-radius: 6px;
  background: rgba(34, 199, 230, 0.07);
}

.selection-title {
  display: flex;
  flex-direction: column;
  gap: 2px;

  strong {
    overflow: hidden;
    color: #fff;
    font-size: 13px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.selection-kicker {
  color: #72def0;
  font-size: 10px;
}

.selection-card dl {
  display: grid;
  gap: 5px;
  margin: 9px 0 0;

  div {
    display: flex;
    justify-content: space-between;
    gap: 12px;
  }

  dt,
  dd {
    margin: 0;
    font-size: 10px;
  }

  dt {
    color: rgba(255, 255, 255, 0.42);
  }
  dd {
    overflow: hidden;
    color: rgba(255, 255, 255, 0.78);
    text-align: right;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .danger {
    color: #ff8a9f;
  }
}

.map-status-bar {
  position: absolute;
  left: 16px;
  bottom: 16px;
  z-index: 15;
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: calc(100% - 364px);
  padding: 7px 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  background: rgba(7, 12, 19, 0.86);
  color: rgba(255, 255, 255, 0.58);
  font-family: 'Geist Mono', monospace;
  font-size: 10px;
  backdrop-filter: blur(8px);
}

.park-card {
  padding: 10px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);

  &:last-child {
    border-bottom: none;
  }
}

.park-card-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.park-code {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.45);
}

.park-stats {
  display: flex;
  gap: 10px;
  margin-top: 6px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.68);
}

.panel-links {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
}

.tracking-link {
  display: inline-block;
  font-size: 13px;
  color: var(--fsd-accent);
  text-decoration: none;

  &.secondary {
    color: rgba(255, 255, 255, 0.55);
  }
}

@media (max-width: 720px) {
  .park-overview-page {
    height: calc(100dvh - 136px);
    min-height: 0;
  }

  .overview-panel {
    top: auto;
    right: 8px;
    bottom: 8px;
    left: 8px;
    width: auto;
    max-height: 42vh;
    overflow: auto;
  }

  .overview-map :deep(.amap-geo-map__level-switcher) {
    top: 8px;
    left: 8px;
  }

  .map-status-bar {
    display: none;
  }

  .route-anomaly-banner {
    top: 112px;
  }
}
</style>
