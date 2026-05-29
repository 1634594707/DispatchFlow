<template>
  <div class="tracking-page">
    <div ref="mapContainer" class="map-container"></div>

    <div v-if="apiError" class="api-alert">
      <span class="api-alert-dot"></span>
      <span>{{ apiError }}</span>
      <button type="button" class="api-alert-retry" @click="bootstrapData">重试</button>
    </div>

    <aside class="side-panel" :class="{ collapsed: panelCollapsed }">
      <button class="panel-toggle" @click="panelCollapsed = !panelCollapsed">
        <RightOutlined v-if="!panelCollapsed" />
        <LeftOutlined v-else />
      </button>

      <div v-if="!panelCollapsed" class="panel-content">
        <header class="panel-header">
          <div class="header-row">
            <div class="header-title-wrap">
              <span class="live-badge" :class="{ live: backendOnline, stream: streamConnected }">
                <span class="live-pulse"></span>
                {{ streamConnected ? 'STREAM' : backendOnline ? 'LIVE' : 'OFFLINE' }}
              </span>
              <h1 class="header-title">园区车辆监控</h1>
            </div>
            <div class="header-actions">
              <router-link class="mobile-entry" to="/mobile/order" title="移动下单">下单</router-link>
              <button class="refresh-btn" :class="{ spinning: refreshing }" title="刷新" @click="manualRefresh">
                <ReloadOutlined />
              </button>
            </div>
          </div>
          <p class="header-sub">
            {{ activeParkName }} · {{ currentTime }}
            <span v-if="streamConnected && lastStreamLatencyMs != null" class="stream-latency">
              · 推送延迟 {{ formatStreamLatency(lastStreamLatencyMs) }}
            </span>
          </p>
        </header>

        <div class="panel-toolbar">
          <label class="toolbar-field">
            <span class="toolbar-label">园区</span>
            <a-select
              v-model:value="selectedParkId"
              class="park-select"
              popup-class-name="park-select-dropdown"
              size="small"
              :loading="loadingParks"
              :options="parkOptions"
              placeholder="选择园区"
              @change="handleParkChange"
            />
          </label>
        </div>

        <div class="stat-strip">
          <button class="stat-item total" :class="{ active: activeFilter === 'all' }" @click="filterByStatus('all')">
            <span class="stat-value">{{ vehicles.length }}</span>
            <span class="stat-label">全部</span>
          </button>
          <button class="stat-item online" :class="{ active: activeFilter === 'ONLINE' }" @click="filterByStatus('ONLINE')">
            <span class="stat-value">{{ onlineCount }}</span>
            <span class="stat-label">在线</span>
          </button>
          <button class="stat-item busy" :class="{ active: activeFilter === 'BUSY' }" @click="filterByStatus('BUSY')">
            <span class="stat-value">{{ busyCount }}</span>
            <span class="stat-label">执行中</span>
          </button>
          <button class="stat-item charging" :class="{ active: activeFilter === 'CHARGING' }" @click="filterByStatus('CHARGING')">
            <span class="stat-value">{{ chargingCount }}</span>
            <span class="stat-label">充电</span>
          </button>
        </div>

        <div class="filter-bar">
          <button
            v-for="item in extraFilterOptions"
            :key="item.value"
            class="filter-chip"
            :class="{ active: activeFilter === item.value }"
            @click="filterByStatus(item.value)"
          >
            {{ item.label }}
          </button>
          <button class="filter-chip filter-chip-layer" :class="{ active: showChargeLayer }" @click="toggleChargeLayer">
            充电图层
          </button>
        </div>

        <div class="panel-body">
          <section class="panel-section">
            <div class="section-head">
              <span class="section-title">车辆</span>
              <span class="section-count">{{ filteredVehicles.length }}</span>
            </div>
            <div class="card-list">
              <button
                v-for="vehicle in filteredVehicles"
                :key="vehicle.vehicleId"
                class="info-card vehicle-card"
                :class="{ selected: selectedId === vehicle.vehicleId, offline: vehicle.onlineStatus === 'OFFLINE' }"
                @click="focusVehicle(vehicle)"
              >
                <div class="vehicle-card-head">
                  <div class="vehicle-id-block">
                    <strong>{{ vehicle.vehicleCode }}</strong>
                    <span class="vehicle-name">{{ vehicle.vehicleName }}</span>
                    <span class="link-mode-pill" :class="vehicle.linkMode === 'REAL' ? 'link-real' : 'link-sim'">
                      {{ vehicle.linkMode === 'REAL' ? 'REAL' : 'SIM' }}
                    </span>
                  </div>
                  <span class="status-dot" :class="vehicle.onlineStatus === 'ONLINE' ? 'dot-online' : 'dot-offline'"></span>
                </div>
                <div class="vehicle-card-meta">
                  <span class="meta-dispatch">{{ dispatchLabel(vehicle.dispatchStatus) }}</span>
                  <span class="stage-pill" :class="stageClass(vehicle.runtimeStage)">
                    {{ stageLabel(vehicle.runtimeStage) }}
                  </span>
                  <span class="meta-battery" :class="{ low: vehicle.lowBattery }">{{ vehicle.batteryLevel }}%</span>
                </div>
                <div v-if="vehicle.targetCode || vehicle.charging || vehicle.lowBattery" class="card-tags">
                  <span v-if="vehicle.targetCode" class="mini-tag target-tag">
                    {{ targetLabel(vehicle.targetType) }} {{ vehicle.targetCode }}
                  </span>
                  <span v-if="vehicle.charging" class="mini-tag charging-tag">充电中</span>
                  <span v-if="vehicle.lowBattery" class="mini-tag risk-tag">低电量</span>
                </div>
              </button>
              <div v-if="filteredVehicles.length === 0" class="empty-state">
                <InboxOutlined />
                <span>暂无车辆</span>
              </div>
            </div>
          </section>

          <section class="panel-section panel-section-orders">
            <div class="section-head">
              <span class="section-title">订单链路</span>
              <span class="section-count">{{ parkOrders.length }}</span>
            </div>
            <div class="card-list order-list">
              <div v-for="order in parkOrders.slice(0, 8)" :key="order.orderId" class="info-card order-card">
                <div class="order-card-head">
                  <router-link :to="`/orders/${order.orderId}`" class="order-link">
                    {{ order.orderNo || `ORDER-${order.orderId}` }}
                  </router-link>
                  <span class="stage-pill" :class="stageClass(order.runtimeStage)">
                    {{ stageLabel(order.runtimeStage) }}
                  </span>
                </div>
                <div class="route-line">
                  <span>{{ order.pickupStation.stationCode }}</span>
                  <span class="route-arrow">→</span>
                  <span>{{ order.dropoffStation.stationCode }}</span>
                </div>
                <div class="order-card-foot">
                  <span>{{ order.vehicleCode || '待分配' }}</span>
                  <span>{{ formatOrderTime(order.updatedAt) }}</span>
                </div>
              </div>
              <div v-if="parkOrders.length === 0" class="empty-state">
                <InboxOutlined />
                <span>暂无订单</span>
              </div>
            </div>
          </section>
        </div>

        <footer class="panel-footer">
          <span>低电量 {{ lowBatteryCount }} 台</span>
          <span class="footer-time">{{ currentTime }}</span>
        </footer>
      </div>
    </aside>

    <div class="legend">
      <div class="legend-item"><span class="legend-dot station-a"></span><span>取货站</span></div>
      <div class="legend-item"><span class="legend-dot station-b"></span><span>送货站</span></div>
      <div class="legend-item"><span class="legend-dot parking"></span><span>停车/充电位</span></div>
      <div class="legend-item"><span class="legend-dot legend-dot-charging"></span><span>充电状态</span></div>
      <div class="legend-item"><span class="legend-dot legend-dot-busy"></span><span>订单链路</span></div>
    </div>

    <div v-if="selectedVehicle" class="detail-mask" @click.self="selectedId = null">
      <div class="detail-card">
        <div class="detail-header">
          <div>
            <div class="detail-code">{{ selectedVehicle.vehicleCode }}</div>
            <div class="detail-name">{{ selectedVehicle.vehicleName }}</div>
          </div>
          <button class="detail-close" @click="selectedId = null">
            <CloseOutlined />
          </button>
        </div>
        <div class="detail-body">
          <div class="detail-row">
            <span>在线状态</span>
            <StatusBadge :status="selectedVehicle.onlineStatus" type="online" />
          </div>
          <div class="detail-row">
            <span>调度状态</span>
            <StatusBadge :status="selectedVehicle.dispatchStatus" type="dispatch" />
          </div>
          <div class="detail-row">
            <span>运行阶段</span>
            <span class="stage-pill" :class="stageClass(selectedVehicle.runtimeStage)">
              {{ stageLabel(selectedVehicle.runtimeStage) }}
            </span>
          </div>
          <div class="detail-row">
            <span>电量</span>
            <span>{{ selectedVehicle.batteryLevel }}%</span>
          </div>
          <div class="detail-row">
            <span>目标点</span>
            <span>{{ formatVehicleTarget(selectedVehicle) }}</span>
          </div>
          <div class="detail-row">
            <span>充电状态</span>
            <span class="detail-flag" :class="{ active: selectedVehicle.charging }">
              {{ selectedVehicle.charging ? '充电中' : '未充电' }}
            </span>
          </div>
          <div class="detail-row">
            <span>电量风险</span>
            <span class="detail-flag" :class="{ danger: selectedVehicle.lowBattery }">
              {{ selectedVehicle.lowBattery ? '低电量' : '正常' }}
            </span>
          </div>
          <div class="detail-row">
            <span>坐标</span>
            <span>{{ selectedVehicle.x.toFixed(0) }}, {{ selectedVehicle.y.toFixed(0) }}</span>
          </div>
          <div v-if="selectedVehicle.currentTaskId" class="detail-row">
            <span>当前任务</span>
            <router-link :to="`/tasks/${selectedVehicle.currentTaskId}`" class="detail-link">
              #{{ selectedVehicle.currentTaskId }}
            </router-link>
          </div>
          <div v-if="selectedVehicle.currentOrderId" class="detail-row">
            <span>当前订单</span>
            <router-link :to="`/orders/${selectedVehicle.currentOrderId}`" class="detail-link">
              #{{ selectedVehicle.currentOrderId }}
            </router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { CloseOutlined, InboxOutlined, LeftOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { getParkLayout, getParkOrders, getParkVehicles, listParks } from '@/api/park'
import { getFleetTelemetryStreamUrl } from '@/api/dispatch'
import { createSSEClient } from '@/utils/sseClient'
import type { SSEClient } from '@/types/stream'
import type {
  ParkLayout,
  ParkOrderSnapshot,
  ParkPoint,
  ParkStation,
  ParkSummary,
  ParkVehicleSnapshot,
} from '@/types/park'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const mapContainer = ref<HTMLElement>()
const panelCollapsed = ref(false)
const activeFilter = ref('all')
const selectedId = ref<number | null>(null)
const refreshing = ref(false)
const showChargeLayer = ref(false)
const currentTime = ref(dayjs().format('HH:mm:ss'))
const vehicles = ref<ParkVehicleSnapshot[]>([])
const parkOrders = ref<ParkOrderSnapshot[]>([])
const parkLayout = ref<ParkLayout | null>(null)
const parks = ref<ParkSummary[]>([])
const selectedParkId = ref<number | undefined>()
const loadingParks = ref(false)
const apiError = ref('')
const backendOnline = ref(false)
const streamConnected = ref(false)
const lastStreamAt = ref<string | null>(null)
const lastStreamLatencyMs = ref<number | null>(null)
let sseClient: SSEClient | null = null
let fallbackPollTimer: ReturnType<typeof setInterval> | null = null

const parkOptions = computed(() =>
  parks.value.map(park => ({
    value: park.parkId,
    label: park.parkName,
  })),
)

const activeParkName = computed(() => {
  const park = parks.value.find(item => item.parkId === selectedParkId.value)
  return park?.parkName || parkLayout.value?.parkName || '默认园区'
})

let map: L.Map | null = null
let markersLayer: L.LayerGroup | null = null
let trajectoryLayer: L.LayerGroup | null = null
let stationLayer: L.LayerGroup | null = null
let orderLayer: L.LayerGroup | null = null
let chargingLayer: L.LayerGroup | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null
let clockTimer: ReturnType<typeof setInterval> | null = null
let currentMarkerScale = 1

const extraFilterOptions = [
  { label: '全部', value: 'all' },
  { label: '仿真车', value: 'SIM' },
  { label: '真实车', value: 'REAL' },
  { label: '低电量', value: 'LOW_BATTERY' },
  { label: '离线', value: 'OFFLINE' },
]

const stageLabels: Record<string, string> = {
  IDLE_PATROL: '空闲巡航',
  STANDBY: '待命中',
  TO_PICKUP: '前往取货',
  HEADING_TO_PICKUP: '前往取货',
  LOADING: '装货中',
  TO_DROPOFF: '配送中',
  HEADING_TO_DROPOFF: '配送中',
  UNLOADING: '卸货中',
  TO_CHARGING: '前往充电',
  CHARGING: '充电中',
  RETURNING_TO_STANDBY: '返回待命',
  PENDING_ASSIGNMENT: '待分配',
  MANUAL_PENDING: '人工介入',
  COMPLETED: '已完成',
  FAILED: '失败',
  OFFLINE: '离线',
}

const onlineCount = computed(() => vehicles.value.filter(vehicle => vehicle.onlineStatus === 'ONLINE').length)
const busyCount = computed(() => vehicles.value.filter(vehicle => vehicle.dispatchStatus === 'BUSY').length)
const chargingCount = computed(() => vehicles.value.filter(vehicle => vehicle.charging).length)
const lowBatteryCount = computed(() => vehicles.value.filter(vehicle => vehicle.lowBattery).length)

const filteredVehicles = computed(() => {
  switch (activeFilter.value) {
    case 'ONLINE':
      return vehicles.value.filter(vehicle => vehicle.onlineStatus === 'ONLINE')
    case 'OFFLINE':
      return vehicles.value.filter(vehicle => vehicle.onlineStatus === 'OFFLINE')
    case 'BUSY':
      return vehicles.value.filter(vehicle => vehicle.dispatchStatus === 'BUSY')
    case 'CHARGING':
      return vehicles.value.filter(vehicle => vehicle.charging)
    case 'LOW_BATTERY':
      return vehicles.value.filter(vehicle => vehicle.lowBattery)
    case 'SIM':
      return vehicles.value.filter(vehicle => (vehicle.linkMode || 'SIM') === 'SIM')
    case 'REAL':
      return vehicles.value.filter(vehicle => vehicle.linkMode === 'REAL')
    default:
      return vehicles.value
  }
})

const selectedVehicle = computed(() => {
  if (!selectedId.value) return null
  return vehicles.value.find(vehicle => vehicle.vehicleId === selectedId.value) || null
})

function stageLabel(stage: string) {
  return stageLabels[stage] || stage || '未知'
}

function stageClass(stage: string) {
  if (stage === 'IDLE_PATROL' || stage === 'STANDBY' || stage === 'COMPLETED') return 'stage-idle'
  if (stage === 'TO_PICKUP' || stage === 'HEADING_TO_PICKUP' || stage === 'TO_DROPOFF' || stage === 'HEADING_TO_DROPOFF') {
    return 'stage-moving'
  }
  if (stage === 'LOADING' || stage === 'UNLOADING') return 'stage-loading'
  if (stage === 'TO_CHARGING' || stage === 'CHARGING' || stage === 'RETURNING_TO_STANDBY') return 'stage-charging'
  if (stage === 'FAILED' || stage === 'MANUAL_PENDING' || stage === 'OFFLINE') return 'stage-risk'
  return 'stage-default'
}

function targetLabel(targetType: string | null) {
  switch (targetType) {
    case 'PICKUP':
      return '取货'
    case 'DROPOFF':
      return '送货'
    case 'CHARGING':
      return '充电'
    case 'STANDBY':
      return '待命'
    default:
      return '目标'
  }
}

function formatVehicleTarget(vehicle: ParkVehicleSnapshot) {
  if (!vehicle.targetCode) return '--'
  return `${targetLabel(vehicle.targetType)} ${vehicle.targetCode}`
}

function dispatchLabel(status: string) {
  if (status === 'IDLE') return '空闲'
  if (status === 'BUSY') return '忙碌'
  return status || '未知'
}

function filterByStatus(status: string) {
  activeFilter.value = status
  updateVehicleMarkers()
}

function formatOrderTime(value: string | null) {
  if (!value) return '--'
  return dayjs(value).fromNow()
}

function toggleChargeLayer() {
  showChargeLayer.value = !showChargeLayer.value
  drawChargeLayer()
}

function markerColor(vehicle: ParkVehicleSnapshot) {
  if (vehicle.onlineStatus === 'OFFLINE') return '#ff4d6d'
  if (vehicle.charging) return '#ffb020'
  if (vehicle.lowBattery) return '#ff7a45'
  if (vehicle.dispatchStatus === 'BUSY') return '#3ea6ff'
  return '#00d68f'
}

function orderColor(stage: string) {
  if (stage === 'COMPLETED') return '#00d68f'
  if (stage === 'FAILED' || stage === 'MANUAL_PENDING') return '#ff4d6d'
  if (stage === 'LOADING' || stage === 'UNLOADING' || stage === 'CHARGING') return '#ffb020'
  return '#3ea6ff'
}

function createVehicleIcon(vehicle: ParkVehicleSnapshot) {
  const color = markerColor(vehicle)
  const stage = stageLabel(vehicle.runtimeStage)
  const batteryText = vehicle.charging ? `充电 ${vehicle.batteryLevel}%` : `电量 ${vehicle.batteryLevel}%`
  return L.divIcon({
    className: 'vehicle-marker-wrap',
    iconSize: [96, 96],
    iconAnchor: [48, 48],
    html: `
      <div class="vehicle-marker" style="--marker-color:${color}">
        <span class="vehicle-core"></span>
        <span class="vehicle-code">${vehicle.vehicleCode}</span>
        <span class="vehicle-stage">${stage}</span>
        <span class="vehicle-battery">${batteryText}</span>
      </div>
    `,
  })
}

function createStationIcon(station: ParkStation) {
  const isPickup = station.area === 'A'
  const color = isPickup ? '#00d68f' : '#ff4d6d'
  const label = isPickup ? '取货站' : '送货站'
  return L.divIcon({
    className: 'station-marker-wrap',
    iconSize: [60, 48],
    iconAnchor: [30, 40],
    html: `
      <div class="station-marker" style="--station-color:${color}">
        <span class="station-label">${label}</span>
        <span class="station-code">${station.stationCode}</span>
      </div>
    `,
  })
}

function createParkingIcon(code: string, mode: 'idle' | 'charging' | 'normal') {
  const color = mode === 'charging' ? '#ffb020' : mode === 'idle' ? '#3ea6ff' : '#8ba2bd'
  const label = mode === 'charging' ? '停车充电位' : '停车位'
  return L.divIcon({
    className: 'parking-marker-wrap',
    iconSize: [60, 38],
    iconAnchor: [30, 32],
    html: `
      <div class="parking-marker" style="--parking-color:${color}">
        <span class="parking-label">${label}</span>
        <span class="parking-code">${code}</span>
      </div>
    `,
  })
}

function createChargeVehicleIcon(vehicle: ParkVehicleSnapshot) {
  const color = vehicle.charging ? '#ffb020' : '#3ea6ff'
  const text = vehicle.charging ? `充电 ${vehicle.batteryLevel}%` : `待命 ${vehicle.batteryLevel}%`
  return L.divIcon({
    className: 'charge-vehicle-wrap',
    iconSize: [84, 52],
    iconAnchor: [42, 26],
    html: `
      <div class="charge-vehicle" style="--charge-color:${color}">
        <span class="charge-vehicle-code">${vehicle.vehicleCode}</span>
        <span class="charge-vehicle-state">${text}</span>
      </div>
    `,
  })
}

function markerScaleForZoom(zoom: number) {
  const scale = Math.pow(2, (zoom - 1) * 0.38)
  return Math.min(1.2, Math.max(0.42, Number(scale.toFixed(3))))
}

function applyMarkerScale() {
  if (!mapContainer.value || !map) return
  const nextScale = markerScaleForZoom(map.getZoom())
  if (nextScale === currentMarkerScale) return
  currentMarkerScale = nextScale
  mapContainer.value.style.setProperty('--map-marker-scale', String(nextScale))
  mapContainer.value.style.setProperty('--map-line-weight-scale', String(Math.max(0.72, Math.min(1.15, nextScale))))
}

function toLatLng(x: number, y: number): L.LatLngExpression {
  if (!parkLayout.value) return [y, x]
  return [parkLayout.value.height - y, x] as L.LatLngExpression
}

function initMap() {
  if (!mapContainer.value) return
  map = L.map(mapContainer.value, {
    crs: L.CRS.Simple,
    minZoom: -1,
    maxZoom: 3,
    zoomControl: false,
    attributionControl: false,
    maxBoundsViscosity: 1,
  })
  L.control.zoom({ position: 'bottomright' as never }).addTo(map)
  stationLayer = L.layerGroup().addTo(map)
  orderLayer = L.layerGroup().addTo(map)
  trajectoryLayer = L.layerGroup().addTo(map)
  markersLayer = L.layerGroup().addTo(map)
  chargingLayer = L.layerGroup().addTo(map)
  map.on('zoom zoomend viewreset resize', applyMarkerScale)
  applyMarkerScale()
}

function loadParkImage() {
  if (!map || !parkLayout.value) return
  const bounds: L.LatLngBoundsExpression = [[0, 0], [parkLayout.value.height, parkLayout.value.width]]
  L.imageOverlay('/park-map.svg', bounds, { interactive: false, opacity: 0.94 }).addTo(map)
  const latLngBounds = L.latLngBounds(bounds)
  map.setMaxBounds(latLngBounds.pad(0.08))
  map.fitBounds(latLngBounds, {
    paddingTopLeft: [420, 30],
    paddingBottomRight: [40, 110],
  })
}

function drawStations() {
  if (!stationLayer || !parkLayout.value) return
  stationLayer.clearLayers()

  parkLayout.value.stations.forEach(station => {
    stationLayer!.addLayer(L.marker(toLatLng(station.x, station.y), { icon: createStationIcon(station), interactive: false }))
  })
}

function drawChargeLayer() {
  if (!chargingLayer || !parkLayout.value) return
  chargingLayer.clearLayers()
  if (!showChargeLayer.value) return

  const occupiedTargets = new Map<string, ParkVehicleSnapshot[]>()
  vehicles.value.forEach(vehicle => {
    if (!vehicle.targetCode) return
    const list = occupiedTargets.get(vehicle.targetCode) || []
    list.push(vehicle)
    occupiedTargets.set(vehicle.targetCode, list)
  })

  parkLayout.value.parkingSpots.forEach(spot => {
    const assignedVehicles = occupiedTargets.get(spot.code) || []
    const chargingVehicle = assignedVehicles.find(vehicle => vehicle.charging)
    const parkedVehicle = assignedVehicles[0]
    const mode = chargingVehicle ? 'charging' : parkedVehicle ? 'idle' : 'normal'

    chargingLayer!.addLayer(
      L.marker(toLatLng(spot.x, spot.y), {
        icon: createParkingIcon(spot.code, mode),
        interactive: false,
      }),
    )

    const vehicleToShow = chargingVehicle || parkedVehicle
    if (vehicleToShow) {
      chargingLayer!.addLayer(
        L.marker(toLatLng(spot.x, spot.y - 34), {
          icon: createChargeVehicleIcon(vehicleToShow),
          interactive: false,
        }),
      )
    }
  })
}

function updateVehicleMarkers() {
  if (!markersLayer || !trajectoryLayer) return
  markersLayer.clearLayers()
  trajectoryLayer.clearLayers()
  const lineWeightScale = Math.max(0.72, Math.min(1.15, currentMarkerScale))

  filteredVehicles.value.forEach(vehicle => {
    if (vehicle.trajectory.length > 1) {
      trajectoryLayer!.addLayer(
        L.polyline(
          vehicle.trajectory.map(point => toLatLng(point.x, point.y)),
          {
            color: markerColor(vehicle),
            weight: 2 * lineWeightScale,
            opacity: 0.35,
            dashArray: '5,7',
          },
        ),
      )
    }

    const marker = L.marker(toLatLng(vehicle.x, vehicle.y), { icon: createVehicleIcon(vehicle) }).on('click', () => {
      selectedId.value = vehicle.vehicleId
    })
    markersLayer!.addLayer(marker)
  })

  drawChargeLayer()
}

function getVehicle(vehicleId: number | null) {
  if (!vehicleId) return null
  return vehicles.value.find(vehicle => vehicle.vehicleId === vehicleId) || null
}

function drawOrderChains() {
  if (!orderLayer) return
  orderLayer.clearLayers()
  const lineWeightScale = Math.max(0.72, Math.min(1.15, currentMarkerScale))

  parkOrders.value.forEach(order => {
    const color = orderColor(order.runtimeStage)
    orderLayer!.addLayer(
      L.polyline(
        [
          toLatLng(order.pickupStation.x, order.pickupStation.y),
          toLatLng(order.dropoffStation.x, order.dropoffStation.y),
        ],
        {
          color,
          weight: 3 * lineWeightScale,
          opacity: 0.55,
          dashArray: order.runtimeStage === 'COMPLETED' ? '4,8' : undefined,
        },
      ),
    )

    const vehicle = getVehicle(order.vehicleId)
    if (!vehicle) return

    const target =
      order.runtimeStage === 'TO_DROPOFF' ||
      order.runtimeStage === 'HEADING_TO_DROPOFF' ||
      order.runtimeStage === 'UNLOADING'
        ? order.dropoffStation
        : order.pickupStation

    orderLayer!.addLayer(
      L.polyline(
        [
          toLatLng(vehicle.x, vehicle.y),
          toLatLng(target.x, target.y),
        ],
        {
          color,
          weight: 2 * lineWeightScale,
          opacity: 0.82,
        },
      ),
    )
  })
}

function focusVehicle(vehicle: ParkVehicleSnapshot) {
  selectedId.value = vehicle.vehicleId
  map?.flyTo(toLatLng(vehicle.x, vehicle.y), 2, { duration: 0.8 })
}

async function fetchParks() {
  loadingParks.value = true
  try {
    const response = await listParks()
    parks.value = response.data || []
    if (!selectedParkId.value) {
      const defaultPark = parks.value.find(p => p.defaultPark) || parks.value[0]
      selectedParkId.value = defaultPark?.parkId
    }
  } finally {
    loadingParks.value = false
  }
}

async function fetchLayout() {
  if (!selectedParkId.value) return
  const response = await getParkLayout(selectedParkId.value)
  parkLayout.value = response.data
  loadParkImage()
  drawStations()
  drawChargeLayer()
}

async function fetchVehicles() {
  const response = await getParkVehicles()
  vehicles.value = response.data || []
  updateVehicleMarkers()
  drawOrderChains()
}

function formatStreamLatency(ms: number) {
  if (ms < 1000) return `${Math.round(ms)}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function applyStreamPayload(data: { ts?: string; vehicles?: ParkVehicleSnapshot[]; parkId?: number }) {
  if (data.parkId != null && selectedParkId.value != null && data.parkId !== selectedParkId.value) {
    return
  }
  if (data.ts) {
    lastStreamAt.value = data.ts
    const parsed = Date.parse(data.ts)
    if (!Number.isNaN(parsed)) {
      lastStreamLatencyMs.value = Math.max(0, Date.now() - parsed)
    }
  }
  if (data.vehicles && Array.isArray(data.vehicles)) {
    vehicles.value = data.vehicles
    updateVehicleMarkers()
    drawOrderChains()
    streamConnected.value = true
    backendOnline.value = true
    apiError.value = ''
  }
}

function initSSEStream() {
  if (sseClient) {
    sseClient.stop()
  }

  const streamUrl = getFleetTelemetryStreamUrl(selectedParkId.value)

  sseClient = createSSEClient({
    url: streamUrl,
    eventName: 'telemetry',
    onMessage: applyStreamPayload,
    onOpen: () => {
      streamConnected.value = true
      backendOnline.value = true
      apiError.value = ''
      stopFallbackPoll()
    },
    onError: () => {
      streamConnected.value = false
    },
    onClose: () => {
      streamConnected.value = false
      startFallbackPoll()
    },
    maxRetries: 10,
    baseDelay: 1000,
    maxDelay: 30000,
  })

  sseClient.start()
}

function startFallbackPoll() {
  if (fallbackPollTimer) return
  fallbackPollTimer = setInterval(() => {
    fetchVehicles().then(() => { backendOnline.value = true }).catch(() => { backendOnline.value = false })
    fetchOrders().catch(() => undefined)
  }, 3000)
}

function stopFallbackPoll() {
  if (fallbackPollTimer) {
    clearInterval(fallbackPollTimer)
    fallbackPollTimer = null
  }
}

async function fetchOrders() {
  const response = await getParkOrders()
  parkOrders.value = response.data || []
  drawOrderChains()
}

async function bootstrapData() {
  apiError.value = ''
  try {
    await fetchParks()
    await fetchLayout()
    await Promise.all([fetchVehicles(), fetchOrders()])
    backendOnline.value = true
  } catch {
    backendOnline.value = false
    apiError.value = '无法连接调度后端（localhost:8080），请启动后点击重试'
  }
}

async function handleParkChange() {
  try {
    await fetchLayout()
    await Promise.all([fetchVehicles(), fetchOrders()])
    initSSEStream()
  } catch {
    apiError.value = '切换园区失败，请确认后端服务正常'
  }
}

async function manualRefresh() {
  refreshing.value = true
  try {
    await Promise.all([fetchVehicles(), fetchOrders()])
    backendOnline.value = true
    apiError.value = ''
  } catch {
    backendOnline.value = false
  } finally {
    setTimeout(() => {
      refreshing.value = false
    }, 500)
  }
}

watch(filteredVehicles, () => {
  updateVehicleMarkers()
})

watch(selectedParkId, (parkId) => {
  if (parkId != null) {
    initSSEStream()
  }
})

onMounted(async () => {
  initMap()
  await bootstrapData()
  initSSEStream()
  clockTimer = setInterval(() => {
    currentTime.value = dayjs().format('HH:mm:ss')
  }, 1000)
})

onUnmounted(() => {
  if (sseClient) {
    sseClient.stop()
    sseClient = null
  }
  stopFallbackPoll()
  if (pollTimer) clearInterval(pollTimer)
  if (clockTimer) clearInterval(clockTimer)
  map?.off('zoom zoomend viewreset resize', applyMarkerScale)
  map?.remove()
  map = null
})
</script>

<style scoped lang="less">
.tracking-page {
  position: absolute;
  inset: 0;
  overflow: hidden;
  --track-bg-deep: #06090f;
  --track-bg-panel: rgba(13, 17, 23, 0.94);
  --track-border: rgba(33, 38, 45, 0.9);
  --track-border-accent: rgba(0, 180, 216, 0.22);
  --track-text: #e6edf3;
  --track-text-muted: #8b949e;
  --track-text-dim: #6e7681;
  --track-accent: #00b4d8;
  --track-accent-soft: rgba(0, 180, 216, 0.1);
  --track-success: #3ddc97;
  --track-busy: #6cb6ff;
  --track-warning: #e3b341;
  --track-danger: #ff7b9c;
  background:
    radial-gradient(ellipse 80% 50% at 15% 0%, rgba(0, 180, 216, 0.06), transparent 50%),
    radial-gradient(ellipse 60% 40% at 85% 10%, rgba(0, 230, 118, 0.04), transparent 45%),
    linear-gradient(180deg, #070b12 0%, var(--track-bg-deep) 100%);
  --map-marker-scale: 1;
  --map-line-weight-scale: 1;
}

.map-container {
  width: 100%;
  height: 100%;
}

:deep(.leaflet-container) {
  background: #070b12;
  font-family: 'IBM Plex Sans', 'Segoe UI', sans-serif;
}

:deep(.leaflet-control-zoom a) {
  background: rgba(6, 12, 22, 0.9) !important;
  color: #d8e4f2 !important;
  border-color: rgba(62, 166, 255, 0.22) !important;
}

:deep(.vehicle-marker-wrap),
:deep(.station-marker-wrap),
:deep(.parking-marker-wrap),
:deep(.charge-vehicle-wrap) {
  background: none !important;
  border: none !important;
}

:deep(.vehicle-marker) {
  position: relative;
  width: 96px;
  height: 96px;
  transform: scale(var(--map-marker-scale));
  transform-origin: center center;
  transition: transform 120ms ease-out;
}

:deep(.vehicle-core) {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: var(--marker-color);
  border: 3px solid rgba(5, 9, 19, 0.92);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--marker-color) 22%, transparent);
  z-index: 3;
}

:deep(.vehicle-code) {
  position: absolute;
  left: 50%;
  top: calc(50% - 30px);
  transform: translateX(-50%);
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(5, 9, 19, 0.88);
  border: 1px solid color-mix(in srgb, var(--marker-color) 40%, transparent);
  color: #ebf2fb;
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  font-weight: 600;
  white-space: nowrap;
  z-index: 2;
}

:deep(.vehicle-stage),
:deep(.vehicle-battery) {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(5, 9, 19, 0.85);
  white-space: nowrap;
  z-index: 2;
}

:deep(.vehicle-stage) {
  top: calc(50% + 16px);
  color: var(--marker-color);
  font-size: 9px;
}

:deep(.vehicle-battery) {
  top: calc(50% + 34px);
  color: #9fb4ca;
  font-family: 'JetBrains Mono', monospace;
  font-size: 9px;
}

:deep(.station-marker) {
  padding: 4px 10px;
  border-radius: 10px;
  background: rgba(5, 9, 19, 0.9);
  border: 1.5px solid var(--station-color);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  transform: scale(var(--map-marker-scale));
  transform-origin: center bottom;
  transition: transform 120ms ease-out;
}

:deep(.station-label) {
  color: var(--station-color);
  font-size: 9px;
  opacity: 0.85;
  letter-spacing: 0.06em;
}

:deep(.station-code) {
  color: var(--station-color);
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  font-weight: 700;
}

:deep(.parking-marker) {
  padding: 3px 8px;
  border-radius: 8px;
  background: rgba(5, 9, 19, 0.88);
  border: 1px solid var(--parking-color);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  transform: scale(var(--map-marker-scale));
  transform-origin: center bottom;
  transition: transform 120ms ease-out;
}

:deep(.parking-label) {
  color: var(--parking-color);
  font-size: 8px;
  opacity: 0.85;
}

:deep(.parking-code) {
  color: var(--parking-color);
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  font-weight: 700;
}

:deep(.charge-vehicle) {
  padding: 4px 8px;
  border-radius: 10px;
  background: rgba(5, 9, 19, 0.92);
  border: 1px solid color-mix(in srgb, var(--charge-color) 48%, transparent);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  transform: scale(var(--map-marker-scale));
  transform-origin: center center;
}

:deep(.charge-vehicle-code) {
  color: #eaf4ff;
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  font-weight: 700;
}

:deep(.charge-vehicle-state) {
  color: var(--charge-color);
  font-size: 9px;
  white-space: nowrap;
}

.side-panel {
  position: absolute;
  top: 16px;
  left: 16px;
  bottom: 16px;
  width: 400px;
  z-index: 1000;
}

.side-panel.collapsed {
  width: 48px;
}

.panel-toggle {
  position: absolute;
  top: 16px;
  right: -14px;
  z-index: 2;
  width: 28px;
  height: 28px;
  border: 1px solid var(--track-border);
  border-left: none;
  border-radius: 0 8px 8px 0;
  background: rgba(13, 17, 23, 0.95);
  color: var(--track-text-muted);
  cursor: pointer;

  &:hover {
    color: var(--track-accent);
    border-color: var(--track-border-accent);
  }
}

.panel-content {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 16px;
  border: 1px solid var(--track-border);
  border-radius: 16px;
  background: var(--track-bg-panel);
  backdrop-filter: blur(16px);
  box-shadow:
    0 16px 40px rgba(0, 0, 0, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.04);
  min-height: 0;
}

.panel-header {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
}

.section-head,
.detail-header,
.detail-row,
.panel-footer,
.route-line,
.header-row,
.header-actions,
.vehicle-card-head,
.vehicle-card-meta,
.order-card-head,
.order-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-row {
  gap: 10px;
  min-width: 0;
}

.header-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.header-actions {
  gap: 6px;
  flex-shrink: 0;
}

.api-alert {
  position: absolute;
  top: 18px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1100;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-radius: 12px;
  border: 1px solid rgba(255, 122, 69, 0.35);
  background: rgba(42, 14, 8, 0.92);
  color: #ffd5c4;
  font-size: 13px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(12px);
}

.api-alert-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #ff7a45;
  box-shadow: 0 0 12px #ff7a45;
}

.api-alert-retry {
  padding: 4px 12px;
  border: 1px solid rgba(255, 176, 32, 0.4);
  border-radius: 999px;
  background: rgba(255, 176, 32, 0.12);
  color: #ffe2a8;
  cursor: pointer;
}

.header-title {
  margin: 0;
  color: var(--track-text);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.02em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

.header-sub {
  margin: 0;
  color: var(--track-text-muted);
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;

  .stream-latency {
    color: #3ecf8e;
    font-variant-numeric: tabular-nums;
  }
}

.live-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  border: 1px solid rgba(255, 61, 113, 0.28);
  background: rgba(255, 61, 113, 0.08);
  color: #ff8fab;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.live-badge.live {
  border-color: rgba(61, 220, 151, 0.3);
  background: rgba(61, 220, 151, 0.08);
  color: #3ddc97;
}

.live-badge.stream {
  border-color: rgba(0, 180, 216, 0.4);
  background: rgba(0, 180, 216, 0.12);
  color: #00b4d8;
}

.live-pulse {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: currentColor;
}

.live-badge.live .live-pulse {
  animation: pulse 1.6s ease-in-out infinite;
}

.panel-toolbar {
  margin-top: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--track-border);
}

.toolbar-field {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.toolbar-label {
  color: var(--track-text-muted);
  font-size: 12px;
  flex-shrink: 0;
  width: 32px;
}

.park-select {
  flex: 1;
  min-width: 0;
}

:deep(.park-select .ant-select-selector) {
  background: rgba(22, 27, 34, 0.9) !important;
  border-color: var(--track-border) !important;
  color: var(--track-text) !important;
  font-family: 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', sans-serif !important;
}

:deep(.park-select .ant-select-selection-item) {
  font-family: 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', sans-serif !important;
  letter-spacing: 0.02em;
}

.mobile-entry {
  padding: 6px 10px;
  border-radius: 8px;
  background: var(--track-accent-soft);
  border: 1px solid var(--track-border-accent);
  color: var(--track-accent);
  text-decoration: none;
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
  flex-shrink: 0;
  transition: background 0.2s ease, border-color 0.2s ease;

  &:hover {
    background: rgba(0, 180, 216, 0.16);
    color: #7ee8ff;
  }
}

.refresh-btn,
.detail-close {
  width: 34px;
  height: 34px;
  border: 1px solid var(--track-border);
  border-radius: 8px;
  background: rgba(22, 27, 34, 0.6);
  color: var(--track-text-muted);
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease;

  &:hover {
    color: var(--track-accent);
    border-color: var(--track-border-accent);
  }
}

.refresh-btn.spinning {
  animation: spin 0.6s linear;
}

.stat-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  margin-top: 12px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 4px;
  border: 1px solid var(--track-border);
  border-radius: 10px;
  background: rgba(22, 27, 34, 0.45);
  color: var(--track-text);
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease;
  text-align: center;
}

.stat-item:hover {
  border-color: rgba(48, 54, 61, 1);
  background: rgba(28, 33, 40, 0.7);
}

.stat-item.active {
  border-color: var(--track-border-accent);
  background: var(--track-accent-soft);
}

.stat-item.online.active {
  border-color: rgba(61, 220, 151, 0.35);
  background: rgba(61, 220, 151, 0.08);
}

.stat-item.busy.active {
  border-color: rgba(108, 182, 255, 0.35);
  background: rgba(0, 180, 216, 0.08);
}

.stat-item.charging.active {
  border-color: rgba(227, 179, 65, 0.35);
  background: rgba(255, 176, 32, 0.08);
}

.stat-item .stat-value {
  font-size: 18px;
  font-weight: 700;
  font-family: 'JetBrains Mono', monospace;
  line-height: 1.2;
}

.stat-item.total .stat-value {
  color: var(--track-text);
}

.stat-item.online .stat-value {
  color: var(--track-success);
}

.stat-item.busy .stat-value {
  color: var(--track-busy);
}

.stat-item.charging .stat-value {
  color: var(--track-warning);
}

.stat-item .stat-label {
  font-size: 10px;
  color: var(--track-text-muted);
  letter-spacing: 0.02em;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.45; transform: scale(0.85); }
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
  padding-bottom: 12px;
}

.filter-chip {
  padding: 5px 10px;
  border: 1px solid var(--track-border);
  border-radius: 999px;
  background: rgba(22, 27, 34, 0.5);
  color: var(--track-text-muted);
  cursor: pointer;
  font-size: 11px;
  line-height: 1.2;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;

  &:hover {
    color: var(--track-text);
    border-color: rgba(48, 54, 61, 1);
  }
}

.filter-chip.active {
  border-color: var(--track-border-accent);
  background: var(--track-accent-soft);
  color: var(--track-accent);
}

.filter-chip-layer {
  margin-left: auto;
}

.panel-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  margin-top: 4px;
  padding-right: 2px;
  display: flex;
  flex-direction: column;
  gap: 0;
}

.panel-section {
  padding-top: 14px;
}

.panel-section-orders {
  margin-top: 4px;
  padding-top: 16px;
  border-top: 1px solid var(--track-border);
}

.section-head {
  margin-bottom: 8px;
  gap: 8px;
  justify-content: flex-start;
}

.section-title {
  color: var(--track-text-muted);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.section-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: rgba(22, 27, 34, 0.8);
  border: 1px solid var(--track-border);
  color: var(--track-text-dim);
  font-size: 11px;
  font-family: 'JetBrains Mono', monospace;
  font-weight: 600;
}

.card-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.order-list {
  padding-bottom: 4px;
}

.info-card {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--track-border);
  border-radius: 10px;
  background: rgba(22, 27, 34, 0.45);
  text-align: left;
  color: var(--track-text);
  transition: border-color 0.2s ease, background 0.2s ease;
}

.vehicle-card {
  cursor: pointer;

  &:hover {
    border-color: rgba(48, 54, 61, 1);
    background: rgba(28, 33, 40, 0.65);
  }
}

.vehicle-card.selected {
  border-color: var(--track-border-accent);
  background: var(--track-accent-soft);
  box-shadow: inset 2px 0 0 var(--track-accent);
}

.vehicle-card.offline {
  opacity: 0.68;
}

.vehicle-id-block {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  strong {
    font-family: 'JetBrains Mono', monospace;
    font-size: 13px;
    color: var(--track-text);
  }
}

.vehicle-name {
  color: var(--track-text-dim);
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.vehicle-card-meta {
  margin-top: 8px;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-start;
}

.meta-dispatch {
  color: var(--track-text-muted);
  font-size: 11px;
}

.meta-battery {
  margin-left: auto;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: var(--track-text-muted);
}

.meta-battery.low {
  color: var(--track-warning);
}

.route-line {
  margin-top: 6px;
  gap: 6px;
  justify-content: flex-start;
  color: var(--track-text-muted);
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;
}

.order-card-foot {
  margin-top: 6px;
  font-size: 11px;
  color: var(--track-text-dim);
}

.card-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.mini-tag,
.detail-flag {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 10px;
  line-height: 1;
}

.mini-tag {
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.target-tag {
  background: rgba(62, 166, 255, 0.1);
  color: #8bcfff;
}

.charging-tag,
.detail-flag.active {
  background: rgba(255, 176, 32, 0.12);
  color: #ffb020;
}

.risk-tag,
.detail-flag.danger {
  background: rgba(255, 122, 69, 0.14);
  color: #ff9f6b;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
}

.dot-online { background: #00d68f; }
.dot-offline { background: #ff4d6d; }

.link-mode-pill {
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.04em;
  vertical-align: middle;
}

.link-sim {
  background: rgba(0, 180, 216, 0.14);
  color: #5fd4ff;
}

.link-real {
  background: rgba(255, 176, 32, 0.16);
  color: #ffc857;
}

.stage-pill {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
}

.stage-idle {
  background: rgba(61, 220, 151, 0.12);
  color: var(--track-success);
}

.stage-moving {
  background: rgba(0, 180, 216, 0.12);
  color: var(--track-busy);
}

.stage-loading {
  background: rgba(255, 176, 32, 0.12);
  color: var(--track-warning);
}

.stage-charging {
  background: rgba(255, 176, 32, 0.1);
  color: #e3b341;
}

.stage-risk {
  background: rgba(255, 61, 113, 0.12);
  color: var(--track-danger);
}

.stage-default {
  background: rgba(110, 118, 129, 0.15);
  color: var(--track-text-muted);
}

.order-card-head {
  gap: 8px;
  min-width: 0;
}

.order-link,
.detail-link {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #eaf4ff;
  text-decoration: none;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
}

.route-arrow {
  color: #58b6ff;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 18px 10px;
  color: #6f88a2;
}

.panel-footer {
  flex-shrink: 0;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--track-border);
  color: var(--track-text-dim);
  font-size: 11px;
}

.footer-time {
  font-family: 'JetBrains Mono', monospace;
  color: var(--track-text-muted);
}

.legend {
  position: absolute;
  right: 18px;
  bottom: 18px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 14px;
  border: 1px solid var(--track-border);
  border-radius: 12px;
  background: rgba(13, 17, 23, 0.88);
  backdrop-filter: blur(12px);
  z-index: 1000;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--track-text-muted);
  font-size: 11px;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  flex-shrink: 0;
}

.legend-dot.station-a {
  background: var(--track-success);
}

.legend-dot.station-b {
  background: var(--track-danger);
}

.legend-dot.parking {
  background: var(--track-accent);
}

.legend-dot.legend-dot-charging {
  background: var(--track-warning);
}

.legend-dot.legend-dot-busy {
  background: var(--track-busy);
}

.detail-mask {
  position: absolute;
  inset: 0;
  display: flex;
  justify-content: flex-end;
  padding: 24px;
  pointer-events: none;
  z-index: 1100;
}

.detail-card {
  width: 320px;
  pointer-events: auto;
  border: 1px solid rgba(62, 166, 255, 0.16);
  border-radius: 18px;
  background: rgba(7, 13, 24, 0.92);
  backdrop-filter: blur(18px);
  box-shadow: 0 18px 44px rgba(0, 0, 0, 0.32);
}

.detail-header {
  padding: 18px 18px 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.detail-code {
  color: #f1f6fb;
  font-family: 'JetBrains Mono', monospace;
  font-size: 16px;
  font-weight: 700;
}

.detail-name {
  margin-top: 4px;
  color: #8aa4be;
  font-size: 13px;
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 18px 18px;
  color: #d9e6f2;
}

.detail-row span:first-child {
  color: #88a1b8;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@media (max-width: 1200px) {
  .side-panel {
    width: 320px;
  }

  .stat-strip {
    grid-template-columns: repeat(2, 1fr);
  }

  .filter-chip-layer {
    margin-left: 0;
  }
}
</style>
