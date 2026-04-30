<template>
  <div class="tracking-page">
    <div ref="mapContainer" class="map-container"></div>

    <aside class="side-panel" :class="{ collapsed: panelCollapsed }">
      <button class="panel-toggle" @click="panelCollapsed = !panelCollapsed">
        <RightOutlined v-if="!panelCollapsed" />
        <LeftOutlined v-else />
      </button>

      <div v-if="!panelCollapsed" class="panel-content">
        <header class="panel-header">
          <div>
            <div class="eyebrow">Park Pilot</div>
            <h1>车辆监控</h1>
          </div>
          <div class="header-actions">
            <router-link class="mobile-entry" to="/mobile/order">移动下单</router-link>
            <button class="refresh-btn" :class="{ spinning: refreshing }" @click="manualRefresh">
              <ReloadOutlined />
            </button>
          </div>
        </header>

        <section class="stat-grid">
          <button class="stat-card" @click="filterByStatus('all')">
            <span class="stat-value">{{ vehicles.length }}</span>
            <span class="stat-label">全部车辆</span>
          </button>
          <button class="stat-card online" @click="filterByStatus('ONLINE')">
            <span class="stat-value">{{ onlineCount }}</span>
            <span class="stat-label">在线</span>
          </button>
          <button class="stat-card busy" @click="filterByStatus('BUSY')">
            <span class="stat-value">{{ busyCount }}</span>
            <span class="stat-label">执行中</span>
          </button>
          <button class="stat-card charging" @click="filterByStatus('CHARGING')">
            <span class="stat-value">{{ chargingCount }}</span>
            <span class="stat-label">充电中</span>
          </button>
        </section>

        <div class="filter-row">
          <button
            v-for="item in filterOptions"
            :key="item.value"
            class="filter-chip"
            :class="{ active: activeFilter === item.value }"
            @click="filterByStatus(item.value)"
          >
            {{ item.label }}
          </button>
        </div>

        <div class="layer-row">
          <button class="filter-chip" :class="{ active: showChargeLayer }" @click="toggleChargeLayer">
            充电图层
          </button>
        </div>

        <section class="section">
          <div class="section-head">
            <span>车辆</span>
            <span>{{ filteredVehicles.length }}</span>
          </div>
          <div class="card-list">
            <button
              v-for="vehicle in filteredVehicles"
              :key="vehicle.vehicleId"
              class="info-card vehicle-card"
              :class="{ selected: selectedId === vehicle.vehicleId, offline: vehicle.onlineStatus === 'OFFLINE' }"
              @click="focusVehicle(vehicle)"
            >
              <div class="card-top">
                <strong>{{ vehicle.vehicleCode }}</strong>
                <span class="status-dot" :class="vehicle.onlineStatus === 'ONLINE' ? 'dot-online' : 'dot-offline'"></span>
              </div>
              <div class="card-name">{{ vehicle.vehicleName }}</div>
              <div class="card-meta">
                <span>{{ dispatchLabel(vehicle.dispatchStatus) }}</span>
                <span class="stage-pill" :class="stageClass(vehicle.runtimeStage)">
                  {{ stageLabel(vehicle.runtimeStage) }}
                </span>
                <span>{{ vehicle.batteryLevel }}%</span>
              </div>
              <div class="card-tags">
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

        <section class="section">
          <div class="section-head">
            <span>订单链路</span>
            <span>{{ parkOrders.length }}</span>
          </div>
          <div class="card-list order-list">
            <div v-for="order in parkOrders.slice(0, 8)" :key="order.orderId" class="info-card order-card">
              <div class="card-top">
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
              <div class="card-meta">
                <span>{{ order.vehicleCode || '待分配车辆' }}</span>
                <span>{{ formatOrderTime(order.updatedAt) }}</span>
              </div>
            </div>
            <div v-if="parkOrders.length === 0" class="empty-state">
              <InboxOutlined />
              <span>暂无订单</span>
            </div>
          </div>
        </section>

        <footer class="panel-footer">
          <span>{{ currentTime }}</span>
          <span>低电量 {{ lowBatteryCount }} 台</span>
        </footer>
      </div>
    </aside>

    <div class="legend">
      <div class="legend-item"><span class="legend-dot station-a"></span><span>取货站</span></div>
      <div class="legend-item"><span class="legend-dot station-b"></span><span>送货站</span></div>
      <div class="legend-item"><span class="legend-dot parking"></span><span>停车/充电位</span></div>
      <div class="legend-item"><span class="legend-dot charging"></span><span>充电状态</span></div>
      <div class="legend-item"><span class="legend-dot busy"></span><span>订单链路</span></div>
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
import { getParkLayout, getParkOrders, getParkVehicles } from '@/api/park'
import type { ParkLayout, ParkOrderSnapshot, ParkPoint, ParkStation, ParkVehicleSnapshot } from '@/types/park'

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

let map: L.Map | null = null
let markersLayer: L.LayerGroup | null = null
let trajectoryLayer: L.LayerGroup | null = null
let stationLayer: L.LayerGroup | null = null
let orderLayer: L.LayerGroup | null = null
let chargingLayer: L.LayerGroup | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null
let clockTimer: ReturnType<typeof setInterval> | null = null
let currentMarkerScale = 1

const filterOptions = [
  { label: '全部', value: 'all' },
  { label: '在线', value: 'ONLINE' },
  { label: '执行中', value: 'BUSY' },
  { label: '充电中', value: 'CHARGING' },
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

async function fetchLayout() {
  const response = await getParkLayout()
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

async function fetchOrders() {
  const response = await getParkOrders()
  parkOrders.value = response.data || []
  drawOrderChains()
}

async function manualRefresh() {
  refreshing.value = true
  try {
    await Promise.all([fetchVehicles(), fetchOrders()])
  } finally {
    setTimeout(() => {
      refreshing.value = false
    }, 500)
  }
}

watch(filteredVehicles, () => {
  updateVehicleMarkers()
})

onMounted(async () => {
  initMap()
  await fetchLayout()
  await Promise.all([fetchVehicles(), fetchOrders()])
  pollTimer = setInterval(() => {
    fetchVehicles()
    fetchOrders()
  }, 3000)
  clockTimer = setInterval(() => {
    currentTime.value = dayjs().format('HH:mm:ss')
  }, 1000)
})

onUnmounted(() => {
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
  background:
    radial-gradient(circle at 20% 20%, rgba(62, 166, 255, 0.18), transparent 28%),
    radial-gradient(circle at 80% 18%, rgba(0, 214, 143, 0.14), transparent 24%),
    linear-gradient(180deg, #08111d 0%, #050913 100%);
  --map-marker-scale: 1;
  --map-line-weight-scale: 1;
}

.map-container {
  width: 100%;
  height: 100%;
}

:deep(.leaflet-container) {
  background: #09101b;
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
  width: 380px;
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
  border: 1px solid rgba(62, 166, 255, 0.28);
  border-left: none;
  border-radius: 0 8px 8px 0;
  background: rgba(6, 12, 22, 0.92);
  color: #9cb5d1;
  cursor: pointer;
}

.panel-content {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 18px;
  border: 1px solid rgba(62, 166, 255, 0.14);
  border-radius: 20px;
  background: rgba(6, 12, 22, 0.86);
  backdrop-filter: blur(18px);
  box-shadow: 0 18px 40px rgba(0, 0, 0, 0.28);
}

.panel-header,
.section-head,
.card-top,
.card-meta,
.detail-header,
.detail-row,
.panel-footer,
.filter-row,
.route-line,
.header-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-actions {
  gap: 8px;
}

.eyebrow {
  color: #58b6ff;
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.panel-header h1 {
  margin: 4px 0 0;
  color: #f4f8fc;
  font-size: 22px;
}

.mobile-entry {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(62, 166, 255, 0.12);
  border: 1px solid rgba(62, 166, 255, 0.24);
  color: #dff0ff;
  text-decoration: none;
  font-size: 12px;
}

.refresh-btn,
.detail-close {
  width: 34px;
  height: 34px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.03);
  color: #b7c9dc;
  cursor: pointer;
}

.refresh-btn.spinning {
  animation: spin 0.6s linear;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-top: 18px;
}

.stat-card {
  padding: 12px 8px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.03);
  color: #dce7f4;
  cursor: pointer;
}

.stat-card.online .stat-value { color: #00d68f; }
.stat-card.busy .stat-value { color: #3ea6ff; }
.stat-card.charging .stat-value { color: #ffb020; }

.stat-value {
  display: block;
  font-size: 20px;
  font-weight: 700;
  font-family: 'JetBrains Mono', monospace;
}

.stat-label {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: #88a2bf;
}

.filter-row,
.layer-row {
  gap: 8px;
  justify-content: flex-start;
  margin-top: 14px;
  flex-wrap: wrap;
}

.layer-row {
  margin-top: 8px;
}

.filter-chip {
  padding: 6px 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.03);
  color: #9fb4ca;
  cursor: pointer;
}

.filter-chip.active {
  border-color: rgba(62, 166, 255, 0.5);
  background: rgba(62, 166, 255, 0.12);
  color: #d7ecff;
}

.section {
  margin-top: 16px;
  min-height: 0;
}

.section-head {
  margin-bottom: 10px;
  color: #9ab0c6;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.card-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 208px;
  overflow: auto;
  padding-right: 4px;
}

.order-list {
  max-height: 190px;
}

.info-card {
  width: 100%;
  padding: 12px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.02));
  text-align: left;
  color: #eef4fb;
}

.vehicle-card {
  cursor: pointer;
}

.vehicle-card.selected {
  border-color: rgba(62, 166, 255, 0.4);
  box-shadow: inset 0 0 0 1px rgba(62, 166, 255, 0.2);
}

.vehicle-card.offline {
  opacity: 0.68;
}

.card-name {
  margin-top: 4px;
  color: #8fa7bf;
  font-size: 12px;
}

.card-meta,
.route-line {
  margin-top: 8px;
  gap: 8px;
  color: #7f96ad;
  font-size: 11px;
}

.card-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 10px;
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

.stage-pill {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
}

.stage-idle {
  background: rgba(0, 214, 143, 0.12);
  color: #00d68f;
}

.stage-moving {
  background: rgba(62, 166, 255, 0.14);
  color: #74c2ff;
}

.stage-loading {
  background: rgba(255, 176, 32, 0.14);
  color: #ffb020;
}

.stage-charging {
  background: rgba(255, 122, 69, 0.14);
  color: #ff9f6b;
}

.stage-risk {
  background: rgba(255, 77, 109, 0.14);
  color: #ff6a87;
}

.stage-default {
  background: rgba(255, 255, 255, 0.08);
  color: #b4c6d8;
}

.order-link,
.detail-link {
  color: #eaf4ff;
  text-decoration: none;
  font-family: 'JetBrains Mono', monospace;
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
  margin-top: auto;
  padding-top: 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  color: #7d94aa;
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;
}

.legend {
  position: absolute;
  right: 18px;
  bottom: 18px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 14px;
  border: 1px solid rgba(62, 166, 255, 0.14);
  border-radius: 14px;
  background: rgba(6, 12, 22, 0.84);
  backdrop-filter: blur(14px);
  z-index: 1000;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #a7bdd3;
  font-size: 11px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
}

.station-a { background: #00d68f; }
.station-b { background: #ff4d6d; }
.parking { background: #3ea6ff; }
.charging { background: #ffb020; }
.busy { background: #58b6ff; }

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

  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
