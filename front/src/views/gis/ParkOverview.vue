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
      <span>地图版本 {{ mapVersionCode }}</span>
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
  getActiveMapVersion,
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
const mapVersionCode = ref<string>('--')
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
  const vehicle = selectedVehicle.value
  const value = vehicle?.lastTelemetryAt
  if (!value) return '无上报时间'
  // 数据年龄 + 服务端统一阈值（路线图 5.1）：超过阈值的数据不允许作为可派依据
  const ageText =
    typeof vehicle?.telemetryAgeSeconds === 'number'
      ? ` · 数据年龄 ${vehicle.telemetryAgeSeconds}s / 阈值 ${vehicle.telemetryStaleThresholdSeconds ?? '-'}s`
      : ''
  const time = new Date(value).toLocaleTimeString('zh-CN', { hour12: false })
  return vehicle?.telemetryStale ? `${time}${ageText} · 数据陈旧，不可派车` : `${time}${ageText}`
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
  // 地图状态栏：拉取当前激活地图数据版本（路线图 5.2）
  const parkId = parkMetadata.value?.parkId ?? 1
  try {
    const versionRes = await getActiveMapVersion(parkId)
    mapVersionCode.value = versionRes.data?.versionCode || '--'
  } catch {
    mapVersionCode.value = '--'
  }
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
  min-height: 520px;
  height: calc(100vh - 64px);
  background: var(--fsd-surface-page);
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
  top: var(--fsd-space-3);
  left: 50%;
  z-index: 20;
  max-width: min(92vw, 520px);
  padding: var(--fsd-space-2) var(--fsd-space-3);
  border-left: 3px solid var(--fsd-error);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-surface-overlay);
  box-shadow: var(--fsd-shadow-popover);
  color: var(--fsd-error);
  font-size: var(--fsd-text-sm);
  pointer-events: none;
  text-align: center;
  transform: translateX(-50%);
}

.overview-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--fsd-text-secondary);

  .hint {
    color: var(--fsd-text-tertiary);
    font-size: var(--fsd-text-xs);
  }

  code {
    color: var(--fsd-accent-strong);
  }
}

.overview-panel {
  position: absolute;
  top: var(--fsd-space-4);
  right: var(--fsd-space-4);
  z-index: 30;
  width: 300px;
  padding: var(--fsd-space-4);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-md);
  background: var(--fsd-surface-overlay);
  box-shadow: var(--fsd-shadow-popover);
  color: var(--fsd-text-primary);

  h1 {
    margin: 0;
    color: var(--fsd-text-heading);
    font-size: 20px;
    letter-spacing: 0;
  }

  .subtitle {
    margin: var(--fsd-space-1) 0 var(--fsd-space-2);
    color: var(--fsd-text-secondary);
    font-size: var(--fsd-text-xs);
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
  gap: var(--fsd-space-1);
  flex: 0 0 auto;
  color: var(--fsd-accent-strong);
  font-size: var(--fsd-text-xs);

  i {
    width: 6px;
    height: 6px;
    border-radius: var(--fsd-radius-full);
    background: var(--fsd-accent);
  }
}

.fleet-stats {
  display: flex;
  gap: var(--fsd-space-3);
  margin-bottom: var(--fsd-space-3);
  color: var(--fsd-text-secondary);
  font-size: var(--fsd-text-xs);
}

.map-legend {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--fsd-space-2);
  padding: var(--fsd-space-2) 0;
  border-block: 1px solid var(--fsd-border-split);

  span {
    display: flex;
    align-items: center;
    gap: var(--fsd-space-1);
    min-width: 0;
    color: var(--fsd-text-secondary);
    font-size: 10px;
    white-space: nowrap;
  }
}

.legend-dot {
  width: 7px;
  height: 7px;
  border-radius: 2px;

  &.pickup {
    background: var(--fsd-success);
  }
  &.dropoff {
    background: var(--fsd-accent);
  }
  &.charging {
    background: var(--fsd-warning);
  }
  &.idle {
    background: var(--fsd-text-tertiary);
  }
}

.selection-card {
  margin: var(--fsd-space-3) 0 var(--fsd-space-1);
  padding: var(--fsd-space-3);
  border-left: 3px solid var(--fsd-accent);
  background: var(--fsd-accent-selected);
}

.selection-title {
  display: flex;
  flex-direction: column;
  gap: 2px;

  strong {
    overflow: hidden;
    color: var(--fsd-text-primary);
    font-size: 13px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.selection-kicker {
  color: var(--fsd-accent-strong);
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
    color: var(--fsd-text-tertiary);
  }
  dd {
    overflow: hidden;
    color: var(--fsd-text-secondary);
    text-align: right;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .danger {
    color: var(--fsd-error);
  }
}

.map-status-bar {
  position: absolute;
  bottom: var(--fsd-space-4);
  left: var(--fsd-space-4);
  z-index: 15;
  display: flex;
  align-items: center;
  gap: var(--fsd-space-3);
  max-width: calc(100% - 364px);
  padding: 7px 10px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-surface-overlay);
  box-shadow: var(--fsd-shadow-popover);
  color: var(--fsd-text-secondary);
  font-family: 'Geist Mono', monospace;
  font-size: 10px;
}

.park-card {
  padding: var(--fsd-space-2) 0;
  border-bottom: 1px solid var(--fsd-border-split);

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
  color: var(--fsd-text-tertiary);
  font-size: var(--fsd-text-xs);
}

.park-stats {
  display: flex;
  gap: var(--fsd-space-3);
  margin-top: var(--fsd-space-1);
  color: var(--fsd-text-secondary);
  font-size: var(--fsd-text-sm);
}

.panel-links {
  display: flex;
  flex-direction: column;
  gap: var(--fsd-space-1);
  margin-top: var(--fsd-space-3);
}

.tracking-link {
  display: inline-block;
  font-size: 13px;
  color: var(--fsd-accent-strong);
  text-decoration: none;

  &.secondary {
    color: var(--fsd-text-secondary);
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
