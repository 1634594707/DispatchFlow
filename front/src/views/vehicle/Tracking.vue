<template>
  <div class="tracking-page" :class="`screen-mode-${screenMode}`">
    <!-- V5-T2: SSE 连接状态指示器 -->
    <div class="sse-status-bar" :class="`sse-${sseStatus}`">
      <span class="sse-status-dot"></span>
      <span class="sse-status-text">{{ sseStatusLabel }}</span>
      <span v-if="sseStatus === 'reconnecting'" class="sse-status-retry">自动重连中...</span>
    </div>

    <SkeletonLoader v-if="showSkeleton" preset="tracking" />
    <template v-else>
    <div
      v-show="showSchematicMap"
      ref="mapContainer"
      class="map-container"
    ></div>
    <AmapGeoMap
      v-if="showGeoMap"
      class="map-container geo-map-layer"
      :center="geoMapCenter"
      :markers="geoMarkers"
      :polygons="geoPolygons"
      :polylines="geoPolylines"
      :circles="geoCircles"
      :zoom="geoMapZoom"
      :fit-view-points="geoFitViewPoints"
      :fit-view-on-change="Boolean(selectedId)"
    />

    <div v-if="apiError" class="api-alert">
      <span class="api-alert-dot"></span>
      <span>{{ apiError }}</span>
      <button type="button" class="api-alert-retry" @click="bootstrapData">重试</button>
    </div>

    <div v-if="showGeoMap && roadRouteWarning" class="api-alert road-route-warning">
      <span class="api-alert-dot warning"></span>
      <span>{{ roadRouteWarning }}</span>
    </div>

    <aside class="side-panel" :class="{ collapsed: panelCollapsed }">
      <button
        class="panel-toggle"
        type="button"
        :title="panelCollapsed ? '展开面板' : '收起面板'"
        :aria-label="panelCollapsed ? '展开面板' : '收起面板'"
        @click="panelCollapsed = !panelCollapsed"
      >
        <LeftOutlined v-if="!panelCollapsed" />
        <RightOutlined v-else />
      </button>

      <div v-if="!panelCollapsed" class="panel-content">
        <header class="panel-header">
          <div class="header-row">
            <div class="header-title-wrap">
              <span class="live-badge" :class="{ live: backendOnline, stream: streamConnected }">
                <span class="live-pulse"></span>
                {{ streamConnected ? 'STREAM' : backendOnline ? 'LIVE' : 'OFFLINE' }}
              </span>
              <h1 class="header-title">找家纺短驳监控</h1>
            </div>
            <div class="header-actions">
              <router-link class="mobile-entry" to="/mobile/order" title="移动下单">下单</router-link>
              <button class="refresh-btn" :class="{ spinning: refreshing }" title="刷新" @click="manualRefresh">
                <ReloadOutlined />
              </button>
            </div>
          </div>
          <p class="header-sub">
            <span class="header-sub-line">{{ activeParkName }} · {{ currentTime }}</span>
            <span v-if="streamConnected && lastStreamLatencyMs != null" class="stream-latency">
              推送延迟 {{ formatStreamLatency(lastStreamLatencyMs) }}
            </span>
          </p>
          <div class="header-controls">
            <p v-if="trackingScene === 'park'" class="map-scope-hint">
              <a-tooltip title="内部路网示意，非真实道路；ZJF 短驳请切换「短驳地理」。">
                <span>园区调度图：内部路网与 AGV 任务，供后台审查。</span>
              </a-tooltip>
            </p>
            <p v-else class="map-scope-hint geo">
              <span class="pilot-badge">当前：叠石桥 L1 试点</span>
              短驳地理图 · 取送货与贴路轨迹
            </p>
            <span class="mode-toggle">
              大屏：
              <a-segmented
                v-model:value="screenMode"
                size="small"
                :options="screenModeOptions"
              />
            </span>
            <span class="mode-toggle">
              模式：{{ peakModeLabel }}
              <a-switch
                v-if="authStore.isAdmin"
                v-model:checked="peakEnabled"
                size="small"
                checked-children="高峰"
                un-checked-children="日常"
                @change="onPeakModeChange"
              />
              <a-switch
                v-model:checked="opsMode"
                size="small"
                checked-children="运维"
                un-checked-children="常规"
                @change="loadOpsSnapshot"
              />
            </span>
          </div>
          <DemoModePanel
            class="demo-mode-header"
            :demo-mode="demoMode"
            :remaining-label="demoRemainingLabel"
            @start="demo.startDemo()"
            @stop="demo.stopDemo()"
            @next="demo.nextDemoOrder()"
          />
        </header>

        <div v-if="screenMode === 'situation'" class="panel-toolbar panel-toolbar-situation">
          <!-- V5-N3: 预测性告警 SOC 区域 -->
        <div class="toolbar-field">
          <span class="toolbar-label">预警</span>
          <a-badge :count="predictiveLowSocVehicles.length" :overflow-count="99" size="small">
            <a-button size="small" :type="predictiveLowSocVehicles.length > 0 ? 'primary' : 'default'" :danger="predictiveLowSocVehicles.length > 0" @click="predictiveDrawerVisible = true">
              <template #icon><BellOutlined /></template>
              SOC 预测
            </a-button>
          </a-badge>
        </div>
        <div class="toolbar-field">
            <span class="toolbar-label">场景</span>
            <a-segmented
              v-model:value="trackingScene"
              size="small"
              class="scene-segment"
              :options="trackingSceneOptions"
            />
          </div>
          <div v-if="trackingScene === 'park'" class="toolbar-field">
            <span class="toolbar-label">园区</span>
            <a-select
              v-model:value="trackingParkId"
              class="park-select"
              :options="trackingParkOptions"
              :loading="parkScope.loading"
              placeholder="选择园区"
              @change="onTrackingParkChange"
            />
          </div>
        </div>

        <div v-if="screenMode === 'incident'" class="incident-toolbar">
          <div class="incident-toolbar-head">
            <span class="incident-title">事件处置</span>
            <span class="incident-hint">优先处理异常车辆与关联订单</span>
          </div>
          <div class="incident-actions">
            <a-badge :count="predictiveLowSocVehicles.length" :overflow-count="99" size="small">
              <a-button size="small" type="primary" danger @click="predictiveDrawerVisible = true">
                <template #icon><BellOutlined /></template>
                低电预警
              </a-button>
            </a-badge>
            <a-button size="small" @click="filterByStatus('LOW_BATTERY')">筛选低电</a-button>
            <router-link to="/field-ops/work-orders">
              <a-button size="small">现场工单</a-button>
            </router-link>
            <a-button size="small" @click="filterByStatus('OFFLINE')">离线车辆</a-button>
          </div>
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
          <button
            class="stat-item low-battery"
            :class="{ active: activeFilter === 'LOW_BATTERY' }"
            @click="filterByStatus('LOW_BATTERY')"
          >
            <span class="stat-value">{{ lowBatteryCount }}</span>
            <span class="stat-label">低电量</span>
          </button>
        </div>

        <div v-if="screenMode === 'situation'" class="filter-bar">
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
          <button class="filter-chip filter-chip-layer" :class="{ active: showL0Circles }" @click="toggleL0Circles">
            L0 双圈
          </button>
        </div>

        <div v-if="screenMode === 'incident' && opsMode && opsSnapshot" class="ops-panel ops-panel-incident">
          <section class="panel-section">
            <div class="section-head"><span class="section-title">低电簇</span></div>
            <div v-for="c in opsSnapshot.lowBatteryClusters" :key="c.gridKey" class="ops-line">
              {{ c.gridKey }} · {{ c.vehicleCount }} 台 · 最低 {{ c.minSoc }}%
            </div>
          </section>
          <section class="panel-section">
            <div class="section-head"><span class="section-title">离线 &gt;5min</span></div>
            <div v-for="v in opsSnapshot.offlineVehicles" :key="v.vehicleId" class="ops-line">
              {{ v.vehicleCode }} · {{ v.offlineMinutes }} 分
            </div>
          </section>
          <section class="panel-section">
            <div class="section-head"><span class="section-title">枢纽排队</span></div>
            <div v-for="t in opsSnapshot.hubQueuedTasks" :key="t.taskId" class="ops-line">
              {{ t.hubStationName }} · {{ t.taskNo }}
            </div>
          </section>
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
                    <span class="link-mode-pill" :class="linkModeClass(vehicle.linkMode)">
                      {{ linkModeLabel(vehicle.linkMode, vehicle.vehicleCode) }}
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
          <div
            v-if="selectedVehicle.longitude != null && selectedVehicle.latitude != null"
            class="detail-row"
          >
            <span>经纬度</span>
            <span>{{ Number(selectedVehicle.longitude).toFixed(6) }}, {{ Number(selectedVehicle.latitude).toFixed(6) }}</span>
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
  </template>

  <!-- V5-N3: SOC 预测告警抽屉 -->
  <a-drawer
    v-model:open="predictiveDrawerVisible"
    title="SOC 趋势预测"
    placement="right"
    width="360"
  >
    <template v-if="predictiveLowSocVehicles.length === 0">
      <div class="empty-state">
        <InboxOutlined />
        <span>暂无预测告警</span>
      </div>
    </template>
    <template v-else>
      <div class="predictive-list">
        <div
          v-for="alert in predictiveLowSocVehicles"
          :key="alert.vehicleId"
          class="predictive-card"
          :class="`trend-${alert.trend}`"
          @click="selectedPredictiveVehicleId = alert.vehicleId"
        >
          <div class="predictive-head">
            <strong>{{ alert.vehicleCode }}</strong>
            <span class="predictive-soc">{{ alert.currentSoc }}%</span>
          </div>
          <div class="predictive-meta">
            <span class="predictive-minutes">预计 {{ alert.predictedMinutes }} 分钟后低于 30%</span>
            <span class="trend-indicator">
              <ArrowUpOutlined v-if="alert.trend === 'stable'" style="color: #52c41a" />
              <ArrowRightOutlined v-else-if="alert.trend === 'slight_decline'" style="color: #faad14" />
              <ArrowDownOutlined v-else style="color: #ff4d4f" />
              {{ alert.trend === 'stable' ? '稳定' : alert.trend === 'slight_decline' ? '缓慢下降' : '快速下降' }}
            </span>
          </div>
        </div>
      </div>
    </template>
    <template v-if="selectedPredictiveVehicleId != null">
      <a-divider>趋势详情</a-divider>
      <div class="trend-detail">
        <p>车辆 {{ selectedPredictiveVehicleId }}</p>
        <p>趋势方向：
          <ArrowUpOutlined v-if="getVehicleTrend(selectedPredictiveVehicleId) === 'stable'" style="color: #52c41a" />
          <ArrowRightOutlined v-else-if="getVehicleTrend(selectedPredictiveVehicleId) === 'slight_decline'" style="color: #faad14" />
          <ArrowDownOutlined v-else style="color: #ff4d4f" />
          {{ getVehicleTrend(selectedPredictiveVehicleId) === 'stable' ? '稳定 ↑' : getVehicleTrend(selectedPredictiveVehicleId) === 'slight_decline' ? '缓慢下降 →' : '快速下降 ↓' }}
        </p>
      </div>
    </template>
  </a-drawer>
</div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { CloseOutlined, InboxOutlined, LeftOutlined, ReloadOutlined, RightOutlined, BellOutlined, ArrowUpOutlined, ArrowRightOutlined, ArrowDownOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import StatusBadge from '@/components/common/StatusBadge.vue'
import SkeletonLoader from '@/components/common/SkeletonLoader.vue'
import AmapGeoMap from '@/components/map/AmapGeoMap.vue'
import DemoModePanel from '@/components/demo/DemoModePanel.vue'
import { getParkGeofences, getParkLayout, getParkOrders, getParkVehicles, getRoadRouteHealth } from '@/api/park'
import type { RoadRouteHealth } from '@/api/park'
import {
  collectRouteFitPoints,
  defaultMapCenter,
  isAmapConfigured,
  toAvGeoMarker,
  buildGeoPolylines,
  stationGeoPosition,
} from '@/maps'
import {
  filterGeoDeliveryOrders,
  filterSchematicOrders,
  filterSchematicParkVehicles,
  filterGeoDeliverySimVehicles,
  filterSchematicStations,
  isGeoDeliverySimVehicle,
  isGeoDeliveryStation,
  isSchematicParkStation,
  isSchematicParkVehicle,
} from '@/maps/stationLayers'
import type { GeoMapMarker, GeoMapPolygon, GeoMapPolyline, GeoMapCircle } from '@/maps'
// V5-D3: Uses shared vehicleToGeoPosition from @/composables/useDeliveryGeo
import { L0_COVERAGE_CIRCLES, vehicleToGeoPosition } from '@/composables/useDeliveryGeo'
import { routeAnomalyWarning } from '@/maps/routeValidation'
import { getFleetTelemetryStreamUrl } from '@/api/dispatch'
import { fetchPeakMode, updatePeakMode, fetchOpsSnapshot, type OpsSnapshot } from '@/api/vertical'
import { useParkScopeStore } from '@/stores/parkScope'
import { useAuthStore } from '@/stores/auth'
import { DEFAULT_TRACKING_SCENE } from '@/config'
import { useDemoMode } from '@/composables/useDemoMode'
import { createSSEClient } from '@/utils/sseClient'
import type { SSEClient } from '@/types/stream'
import { usePredictiveAlert } from '@/composables/usePredictiveAlert'
import type {
  ParkGeofence,
  ParkLayout,
  ParkOrderSnapshot,
  ParkStation,
  ParkVehicleSnapshot,
} from '@/types/park'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const route = useRoute()
const parkScope = useParkScopeStore()
const authStore = useAuthStore()
const peakEnabled = ref(false)
const peakTemplateCode = ref('DAILY')
const opsMode = ref(false)
const opsSnapshot = ref<OpsSnapshot | null>(null)

type ScreenMode = 'situation' | 'incident'
const screenMode = ref<ScreenMode>('situation')
const screenModeOptions = [
  { label: '态势', value: 'situation' as ScreenMode },
  { label: '事件', value: 'incident' as ScreenMode },
]

const demo = useDemoMode()
const demoMode = computed(() => demo.demoMode.value)
const demoRemainingLabel = computed(() => demo.remainingLabel.value)

// V5-N3: 预测性告警
const predictiveAlertStore = usePredictiveAlert()
const predictiveLowSocVehicles = computed(() => predictiveAlertStore.predictLowSocVehicles.value)
const predictiveDrawerVisible = ref(false)
const selectedPredictiveVehicleId = ref<number | null>(null)

function getVehicleTrend(vehicleId: number) {
  return predictiveAlertStore.getVehicleTrend(vehicleId)
}

type TrackingScene = 'park' | 'delivery'
const TRACKING_SCENE_KEY = 'fsd_tracking_scene'

function loadTrackingScene(): TrackingScene {
  const sceneParam = route.query.scene
  if (sceneParam === 'park') return 'park'
  if (sceneParam === 'delivery') return 'delivery'
  if (route.query.mode === 'geo') return 'delivery'
  const stored = localStorage.getItem(TRACKING_SCENE_KEY)
  if (stored === 'delivery' || stored === 'park') return stored
  return DEFAULT_TRACKING_SCENE
}

const trackingScene = ref<TrackingScene>(loadTrackingScene())
const trackingSceneOptions = [
  { label: '园区调度', value: 'park' as const },
  { label: '短驳地理', value: 'delivery' as const },
]

const mapViewMode = ref<'schematic' | 'geo'>(trackingScene.value === 'delivery' && isAmapConfigured() ? 'geo' : 'schematic')
const geoMapAvailable = isAmapConfigured()
const geoMapZoom = 15

const showSchematicMap = computed(() => trackingScene.value === 'park')
const showGeoMap = computed(() => trackingScene.value === 'delivery' && geoMapAvailable)

const trackingParkId = ref<number | undefined>()
const trackingParkOptions = computed(() =>
  parkScope.parks.map(park => ({
    value: park.parkId,
    label: park.parkName,
  })),
)

const schematicOrdersOnMap = computed(() => filterSchematicOrders(parkOrders.value))

const schematicVehiclesOnMap = computed(() => filterSchematicParkVehicles(filteredVehicles.value))

const geoVehiclesOnMap = computed(() => filterGeoDeliverySimVehicles(filteredVehicles.value))

const mapContainer = ref<HTMLElement>()
const panelCollapsed = ref(false)
const activeFilter = ref('all')
const selectedId = ref<number | null>(null)
const refreshing = ref(false)
const showChargeLayer = ref(false)
const showL0Circles = ref(
  route.query.l0 === '1' || route.query.l0circles === '1' || localStorage.getItem('fsd_tracking_l0_circles') === 'true',
)
const currentTime = ref(dayjs().format('HH:mm:ss'))
const vehicles = ref<ParkVehicleSnapshot[]>([])
const parkOrders = ref<ParkOrderSnapshot[]>([])
const parkLayout = ref<ParkLayout | null>(null)
const parkGeofences = ref<ParkGeofence[]>([])
const apiError = ref('')
const backendOnline = ref(false)
const streamConnected = ref(false)
const sseReconnecting = ref(false)
const sseStatus = computed(() => {
  if (streamConnected.value) return 'connected'
  if (sseReconnecting.value) return 'reconnecting'
  return 'disconnected'
})
const sseStatusLabel = computed(() => {
  if (streamConnected.value) return '已连接'
  if (sseReconnecting.value) return '重连中'
  return '已断连'
})
const showSkeleton = computed(() => vehicles.value.length === 0 && apiError.value === '')
const lastStreamAt = ref<string | null>(null)
const lastStreamLatencyMs = ref<number | null>(null)
let sseClient: SSEClient | null = null
let fallbackPollTimer: ReturnType<typeof setInterval> | null = null

const effectiveParkId = computed(() => parkScope.resolveLayoutParkId())

const activeParkName = computed(() => {
  if (trackingScene.value === 'delivery') return '叠石桥短驳试点'
  const parkId = trackingParkId.value ?? effectiveParkId.value
  const park = parkScope.parks.find((item) => item.parkId === parkId)
  return park?.parkName || parkLayout.value?.parkName || '默认园区'
})

function syncTrackingParkSelection() {
  const resolved = parkScope.resolveLayoutParkId()
  if (resolved != null) {
    trackingParkId.value = resolved
  } else if (parkScope.parks[0]) {
    trackingParkId.value = parkScope.parks[0].parkId
  }
}

function applyTrackingScene(scene: TrackingScene) {
  localStorage.setItem(TRACKING_SCENE_KEY, scene)
  if (scene === 'park') {
    mapViewMode.value = 'schematic'
    syncTrackingParkSelection()
    if (trackingParkId.value != null) {
      parkScope.setParkId(trackingParkId.value)
    }
    nextTick(() => {
      if (!map && mapContainer.value) initMap()
      if (map && parkLayout.value) {
        loadParkImage()
        drawStations()
        updateVehicleMarkers()
        drawOrderChains()
      }
    })
  } else {
    mapViewMode.value = geoMapAvailable ? 'geo' : 'schematic'
  }
}

function onTrackingParkChange(parkId: number | undefined) {
  if (parkId == null) return
  trackingParkId.value = parkId
  parkScope.setParkId(parkId)
}

const peakModeLabel = computed(() =>
  peakEnabled.value
    ? `高峰 · ${peakTemplateCode.value === 'TEXTILE_PROMO' ? '家纺大促' : peakTemplateCode.value}`
    : '日常生产',
)

async function loadPeakModeState() {
  const parkId = effectiveParkId.value
  if (!parkId) return
  try {
    const state = (await fetchPeakMode(parkId)).data
    peakEnabled.value = state.mode === 'PEAK'
    peakTemplateCode.value = state.templateCode || 'DAILY'
  } catch {
    peakEnabled.value = false
  }
}

async function onPeakModeChange(checked: boolean) {
  const parkId = effectiveParkId.value
  if (!parkId || !authStore.isAdmin) return
  await updatePeakMode({
    parkId,
    mode: checked ? 'PEAK' : 'NORMAL',
    templateCode: peakTemplateCode.value,
  })
}

async function loadOpsSnapshot() {
  if (!opsMode.value) {
    opsSnapshot.value = null
    return
  }
  try {
    opsSnapshot.value = (await fetchOpsSnapshot(effectiveParkId.value ?? undefined)).data
  } catch {
    opsSnapshot.value = null
  }
}

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
  { label: 'VDA5050', value: 'VDA5050' },
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
      return vehicles.value.filter(vehicle => isSchematicParkVehicle(vehicle) || isGeoDeliverySimVehicle(vehicle))
    case 'REAL':
      return vehicles.value.filter(vehicle => vehicle.linkMode === 'REAL')
    case 'VDA5050':
      return vehicles.value.filter(vehicle => vehicle.linkMode === 'VDA5050')
    default:
      return vehicles.value
  }
})

const geoMapCenter = computed((): [number, number] => {
  const layout = parkLayout.value
  if (layout?.centerLng != null && layout?.centerLat != null) {
    return [Number(layout.centerLng), Number(layout.centerLat)]
  }
  return defaultMapCenter()
})

/** @deprecated Use vehicleToGeoPosition from @/composables/useDeliveryGeo instead */
function vehicleGeoPosition(vehicle: ParkVehicleSnapshot): [number, number] {
  return vehicleToGeoPosition(vehicle)
}

const geoMarkers = computed((): GeoMapMarker[] => {
  const stationMarkers =
    parkLayout.value?.stations
      .filter(isGeoDeliveryStation)
      .flatMap((station) => {
        const position = stationGeoPosition(station)
        if (!position) return []
        return [{ id: `st-${station.stationId}`, position, label: station.stationCode }]
      }) ?? []

  const chargeMarkers =
    showChargeLayer.value && trackingScene.value === 'delivery'
      ? (parkLayout.value?.stations ?? [])
          .filter(station => (station.stationCode ?? '').startsWith('ZJF-CHG-'))
          .flatMap((station) => {
            const position = stationGeoPosition(station)
            if (!position) return []
            const occupied = vehicles.value.filter(
              v => v.charging && v.targetCode === station.stationCode,
            ).length
            return [{
              id: `chg-${station.stationId}`,
              position,
              label: `⚡ ${station.stationCode}${occupied ? ' · 占用' : ''}`,
            }]
          })
      : []

  return [
    ...stationMarkers,
    ...chargeMarkers,
    ...geoVehiclesOnMap.value.map((vehicle) =>
      toAvGeoMarker(String(vehicle.vehicleId), vehicleGeoPosition(vehicle), {
        onlineStatus: vehicle.onlineStatus,
        dispatchStatus: vehicle.dispatchStatus,
        charging: vehicle.charging,
        lowBattery: vehicle.lowBattery,
        batteryStatus: vehicle.batteryStatus,
        heading: vehicle.heading ?? null,
        label: `${shortVehicleCode(vehicle.vehicleCode)} · ${vehicle.batteryLevel}%`,
      }),
    ),
  ]
})

const geoPolygons = computed((): GeoMapPolygon[] =>
  parkGeofences.value
    .filter((fence) => fence.status === 'ACTIVE' && fence.polygon?.length >= 3)
    .map((fence) => ({
      id: String(fence.id),
      path: fence.polygon.map((point) => [Number(point[0]), Number(point[1])] as [number, number]),
      strokeColor: fence.fenceType === 'RESTRICTED' ? '#ff6b6b' : '#00d4aa',
      fillColor: fence.fenceType === 'RESTRICTED' ? 'rgba(255, 107, 107, 0.15)' : 'rgba(0, 212, 170, 0.12)',
      zIndex: 10,
    })),
)

const geoPolylines = computed((): GeoMapPolyline[] =>
  buildGeoPolylines(geoVehiclesOnMap.value, filterGeoDeliveryOrders(parkOrders.value), {
    includeOrderLines: false,
    focusVehicleId: selectedId.value,
  }),
)

const geoCircles = computed((): GeoMapCircle[] =>
  showL0Circles.value ? L0_COVERAGE_CIRCLES : [],
)

const selectedVehicle = computed(() => {
  if (!selectedId.value) return null
  return vehicles.value.find(vehicle => vehicle.vehicleId === selectedId.value) || null
})

const roadRouteHealth = ref<RoadRouteHealth | null>(null)

const geoFitViewPoints = computed((): [number, number][] => {
  if (!showGeoMap.value) return []
  const routePoints = collectRouteFitPoints(geoVehiclesOnMap.value, {
    focusVehicleId: selectedId.value,
  })
  if (routePoints.length >= 2) return routePoints
  return []
})

const roadRouteWarning = computed(() => {
  const anomaly = routeAnomalyWarning(geoVehiclesOnMap.value)
  if (anomaly) return anomaly
  const health = roadRouteHealth.value
  if (health && !health.amapDriving && !health.localGraph) {
    return '道路路径不可用：请配置 FSD_AMAP_WEB_SERVICE_KEY 或检查本地路网。'
  }
  const busyVehicles = geoVehiclesOnMap.value.filter(v => v.dispatchStatus === 'BUSY')
  const hasStraightLine = busyVehicles.some(
    v => v.plannedRouteGeo && v.plannedRouteGeo.length === 2 && v.routeSource === 'STRAIGHT_LINE',
  )
  const hasLowVertices = busyVehicles.some(
    v => v.plannedRouteGeo && v.plannedRouteGeo.length > 0 && v.plannedRouteGeo.length < 4,
  )
  if (hasStraightLine) return '当前为直线模式，请配置高德 Web 服务 Key 或本地路网。'
  if (hasLowVertices) return '计划路径顶点不足（< 4），路线可能不贴路。'
  if (health?.fallbackCount && health.fallbackCount > 0 && !health.amapDriving) {
    return `本地路网兜底中（近 1h 直线降级 ${health.fallbackCount} 次）。`
  }
  return ''
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

function linkModeLabel(mode?: string, vehicleCode?: string) {
  if (mode === 'REAL') return 'REAL'
  if (mode === 'VDA5050') return 'VDA5050'
  if (vehicleCode?.startsWith('ZJF-AV-')) return '短驳仿真'
  return '园区仿真'
}

function linkModeClass(mode?: string) {
  if (mode === 'REAL') return 'link-real'
  if (mode === 'VDA5050') return 'link-vda5050'
  return 'link-sim'
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

function toggleL0Circles() {
  showL0Circles.value = !showL0Circles.value
  localStorage.setItem('fsd_tracking_l0_circles', String(showL0Circles.value))
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

function shortVehicleCode(code: string) {
  const parts = code.split('-')
  if (parts.length >= 2) return `${parts[0]}-${parts[1]}`
  return code.length > 10 ? `${code.slice(0, 10)}…` : code
}

const LABEL_SLOTS = [
  { codeY: -28, stageY: 16, batteryY: 32 },
  { codeY: -28, stageY: 16, batteryY: 32, codeX: 42 },
  { codeY: -28, stageY: 16, batteryY: 32, codeX: -42 },
  { codeY: 18, stageY: 36, batteryY: 52 },
  { codeY: 18, stageY: 36, batteryY: 52, codeX: 42 },
  { codeY: 18, stageY: 36, batteryY: 52, codeX: -42 },
]

function vehicleDistance(a: ParkVehicleSnapshot, b: ParkVehicleSnapshot) {
  const dx = a.x - b.x
  const dy = a.y - b.y
  return Math.hypot(dx, dy)
}

function labelSlotForVehicle(vehicle: ParkVehicleSnapshot, list: ParkVehicleSnapshot[]) {
  const nearbyBefore = list.filter(
    (item) =>
      item.vehicleId !== vehicle.vehicleId &&
      list.indexOf(item) < list.indexOf(vehicle) &&
      vehicleDistance(item, vehicle) < 72,
  ).length
  return nearbyBefore % LABEL_SLOTS.length
}

function createVehicleIcon(vehicle: ParkVehicleSnapshot, expanded = false, slot = 0) {
  const color = markerColor(vehicle)
  if (!expanded) {
    return L.divIcon({
      className: 'vehicle-marker-wrap vehicle-marker-wrap--compact',
      iconSize: [28, 28],
      iconAnchor: [14, 14],
      html: `
        <div class="vehicle-marker vehicle-marker--compact" style="--marker-color:${color}">
          <span class="vehicle-core"></span>
        </div>
      `,
    })
  }

  const stage = stageLabel(vehicle.runtimeStage)
  const batteryText = vehicle.charging ? `${vehicle.batteryLevel}%` : `${vehicle.batteryLevel}%`
  const code = shortVehicleCode(vehicle.vehicleCode)
  const offset = LABEL_SLOTS[slot] || LABEL_SLOTS[0]
  const codeX = offset.codeX ?? 0
  const codeTransform = codeX ? `translate(calc(-50% + ${codeX}px), 0)` : 'translateX(-50%)'
  const stageTransform = codeX ? `translate(calc(-50% + ${codeX}px), 0)` : 'translateX(-50%)'
  const batteryTransform = codeX ? `translate(calc(-50% + ${codeX}px), 0)` : 'translateX(-50%)'

  return L.divIcon({
    className: 'vehicle-marker-wrap',
    iconSize: [96, 96],
    iconAnchor: [48, 48],
    html: `
      <div class="vehicle-marker vehicle-marker--expanded" style="--marker-color:${color}">
        <span class="vehicle-core"></span>
        <span class="vehicle-code" style="top:calc(50% + ${offset.codeY}px);transform:${codeTransform}">${code}</span>
        <span class="vehicle-stage" style="top:calc(50% + ${offset.stageY}px);transform:${stageTransform}">${stage}</span>
        <span class="vehicle-battery" style="top:calc(50% + ${offset.batteryY}px);transform:${batteryTransform}">${batteryText}</span>
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

  filterSchematicStations(parkLayout.value.stations).forEach(station => {
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

  const vehiclesOnMap = showSchematicMap.value ? schematicVehiclesOnMap.value : filteredVehicles.value

  vehiclesOnMap.forEach(vehicle => {
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

    const expanded = selectedId.value === vehicle.vehicleId
    const slot = labelSlotForVehicle(vehicle, vehiclesOnMap)
    const marker = L.marker(toLatLng(vehicle.x, vehicle.y), {
      icon: createVehicleIcon(vehicle, expanded, slot),
      zIndexOffset: expanded ? 1000 : 0,
    }).on('click', () => {
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

  schematicOrdersOnMap.value.forEach(order => {
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

    if (!isSchematicParkStation(target)) return

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
  if (trackingScene.value === 'delivery') return
  map?.flyTo(toLatLng(vehicle.x, vehicle.y), 2, { duration: 0.8 })
}

function applyRouteFocus() {
  const vehicleIdRaw = route.query.vehicleId
  const orderIdRaw = route.query.orderId
  if (route.query.mode === 'geo') {
    trackingScene.value = 'delivery'
  }

  let vehicleId = vehicleIdRaw != null ? Number(vehicleIdRaw) : null
  if ((vehicleId == null || Number.isNaN(vehicleId)) && orderIdRaw != null) {
    const orderId = Number(orderIdRaw)
    const order = parkOrders.value.find(item => item.orderId === orderId)
    vehicleId = order?.vehicleId ?? null
  }
  if (vehicleId == null || Number.isNaN(vehicleId)) return

  const vehicle = vehicles.value.find(item => item.vehicleId === vehicleId)
  if (vehicle) {
    focusVehicle(vehicle)
  } else {
    selectedId.value = vehicleId
  }
}

async function fetchLayout() {
  const parkId = effectiveParkId.value
  if (!parkId) return
  const response = await getParkLayout(parkId)
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
  const layoutParkId = effectiveParkId.value
  if (data.parkId != null && layoutParkId != null && data.parkId !== layoutParkId) {
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
    sseReconnecting.value = false
    backendOnline.value = true
    apiError.value = ''
  }
}

function initSSEStream() {
  if (sseClient) {
    sseClient.stop()
  }

  sseClient = createSSEClient({
    url: () => getFleetTelemetryStreamUrl(effectiveParkId.value),
    eventName: 'telemetry',
    onMessage: applyStreamPayload,
    onOpen: () => {
      streamConnected.value = true
      sseReconnecting.value = false
      backendOnline.value = true
      apiError.value = ''
      stopFallbackPoll()
    },
    onError: () => {
      streamConnected.value = false
      sseReconnecting.value = true
    },
    onClose: () => {
      streamConnected.value = false
      sseReconnecting.value = true
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

async function fetchGeofences() {
  const parkId = effectiveParkId.value
  if (!parkId) {
    parkGeofences.value = []
    return
  }
  try {
    const response = await getParkGeofences(parkId)
    parkGeofences.value = response.data || []
  } catch {
    parkGeofences.value = []
  }
}

async function fetchRoadRouteHealth() {
  if (!showGeoMap.value) return
  try {
    const response = await getRoadRouteHealth()
    roadRouteHealth.value = response.data ?? null
  } catch {
    roadRouteHealth.value = null
  }
}

async function bootstrapData() {
  apiError.value = ''
  try {
    if (parkScope.parks.length === 0) {
      await parkScope.loadParks()
    }
    parkScope.ensureValidSelection()
    syncTrackingParkSelection()
    if (trackingScene.value === 'park' && trackingParkId.value != null) {
      parkScope.setParkId(trackingParkId.value)
    }
    await fetchLayout()
    await Promise.all([fetchVehicles(), fetchOrders(), fetchGeofences(), loadPeakModeState(), fetchRoadRouteHealth()])
    backendOnline.value = true
  } catch {
    backendOnline.value = false
    apiError.value = '无法连接调度后端（localhost:8080），请启动后点击重试'
  }
}

async function handleParkChange() {
  try {
    await fetchLayout()
    await Promise.all([fetchVehicles(), fetchOrders(), fetchGeofences()])
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

watch(selectedId, () => {
  updateVehicleMarkers()
})

watch(
  () => parkScope.scopeVersion,
  async () => {
    syncTrackingParkSelection()
    await handleParkChange()
  },
)

watch(trackingScene, (scene, prev) => {
  if (scene === prev) return
  applyTrackingScene(scene)
  if (scene === 'delivery') {
    void fetchRoadRouteHealth()
  }
})

watch(mapViewMode, (mode) => {
  if (trackingScene.value !== 'park' && mode === 'schematic' && geoMapAvailable) {
    mapViewMode.value = 'geo'
    return
  }
  if (mode === 'schematic' || (mode === 'geo' && !geoMapAvailable)) {
    nextTick(() => {
      if (!map && mapContainer.value && showSchematicMap.value) initMap()
      if (map && parkLayout.value) {
        loadParkImage()
        drawStations()
        updateVehicleMarkers()
        drawOrderChains()
      }
    })
  }
})

watch(showSchematicMap, (visible) => {
  if (!visible) return
  nextTick(() => {
    if (!map && mapContainer.value) initMap()
    if (map && parkLayout.value) {
      loadParkImage()
      drawStations()
      updateVehicleMarkers()
      drawOrderChains()
    }
  })
})

onMounted(async () => {
  await bootstrapData()
  applyTrackingScene(trackingScene.value)
  applyRouteFocus()
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

.screen-mode-incident {
  .side-panel .panel-content {
    border-left-color: rgba(255, 61, 113, 0.35);
  }

  .stat-item.low-battery .stat-value,
  .vehicle-card.offline .vehicle-id-block strong {
    color: var(--track-danger);
  }
}

.incident-toolbar {
  padding: 10px 14px;
  margin: 0 12px 8px;
  border-radius: 10px;
  border: 1px solid rgba(255, 61, 113, 0.25);
  background: rgba(255, 61, 113, 0.08);
}

.incident-toolbar-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 8px;
}

.incident-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--track-danger);
}

.incident-hint {
  font-size: 11px;
  color: var(--track-text-muted);
}

.incident-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ops-panel-incident {
  border: 1px solid rgba(255, 61, 113, 0.2);
  background: rgba(255, 61, 113, 0.05);
}

.map-container {
  width: 100%;
  height: 100%;
}

.geo-map-layer {
  position: absolute;
  inset: 0;
  z-index: 1;

  :deep(.amap-marker-label) {
    padding: 5px 9px;
    border: 1px solid rgba(0, 180, 216, 0.38);
    border-radius: 999px;
    background: rgba(6, 9, 15, 0.88);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.32), 0 0 0 1px rgba(255, 255, 255, 0.04) inset;
    color: var(--track-text);
    font-size: 11px;
    font-weight: 700;
    line-height: 1;
    letter-spacing: 0.03em;
    transform: translateY(-4px);
    white-space: nowrap;
    backdrop-filter: blur(10px);
  }
}

.map-mode-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}

.map-mode-btn {
  border: none;
  border-radius: 6px;
  padding: 2px 10px;
  font-size: 12px;
  line-height: 20px;
  color: var(--track-text-muted);
  background: transparent;
  cursor: pointer;
  transition: color 120ms ease, background 120ms ease;

  &.active {
    color: var(--track-text);
    background: rgba(0, 180, 216, 0.18);
  }

  &.disabled,
  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
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

:deep(.vehicle-marker--compact) {
  width: 28px;
  height: 28px;
}

:deep(.vehicle-marker-wrap--compact) {
  pointer-events: auto;
}

:deep(.vehicle-marker--compact .vehicle-core) {
  width: 14px;
  height: 14px;
  border-width: 2px;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--marker-color) 22%, transparent);
}

:deep(.vehicle-marker--expanded) {
  z-index: 2;
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
  pointer-events: none;
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
  pointer-events: none;
}

:deep(.vehicle-stage) {
  color: var(--marker-color);
  font-size: 9px;
}

:deep(.vehicle-battery) {
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
  width: 0;
  overflow: visible;
}

.panel-toggle {
  position: absolute;
  top: 16px;
  right: -14px;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid var(--track-border);
  border-left: none;
  border-radius: 0 8px 8px 0;
  background: rgba(13, 17, 23, 0.95);
  color: var(--track-text-muted);
  cursor: pointer;
  box-shadow: 2px 0 12px rgba(0, 0, 0, 0.25);
  transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease;

  &:hover {
    color: var(--track-accent);
    border-color: var(--track-border-accent);
    background: rgba(13, 17, 23, 0.98);
  }
}

.side-panel.collapsed .panel-toggle {
  right: auto;
  left: 16px;
  border-left: 1px solid var(--track-border);
  border-right: none;
  border-radius: 8px 0 0 8px;
  box-shadow: -2px 0 12px rgba(0, 0, 0, 0.25);
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

.api-alert-dot.warning {
  background: #ffb020;
  box-shadow: 0 0 12px #ffb020;
}

.road-route-warning {
  top: 60px;
  border-color: rgba(255, 176, 32, 0.35);
  background: rgba(40, 30, 10, 0.92);
  color: #ffe2a8;
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
  line-height: 1.5;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 10px;

  .stream-latency {
    color: #3ecf8e;
    font-variant-numeric: tabular-nums;
  }
}

.header-sub-line {
  min-width: 0;
}

.header-controls {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  margin-top: 10px;
}

.demo-mode-header {
  margin-top: 10px;
  width: 100%;
}

.map-scope-hint {
  margin: 0;
  width: 100%;
  font-size: 11px;
  line-height: 1.45;
  color: var(--track-warning);
}

.pilot-badge {
  display: inline-block;
  margin-right: 8px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  color: #7ee787;
  background: rgba(0, 214, 170, 0.12);
  border: 1px solid rgba(0, 214, 170, 0.35);
}

.map-scope-hint.geo {
  color: var(--track-accent);
}

.mode-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--track-text-muted);
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
  display: flex;
  flex-direction: column;
  gap: 10px;
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
  width: 36px;
}

.scene-segment {
  flex: 1;
  min-width: 0;
}

:deep(.scene-segment.ant-segmented) {
  background: rgba(22, 27, 34, 0.9);
  border: 1px solid var(--track-border);
}

:deep(.scene-segment .ant-segmented-item-label) {
  font-size: 12px;
  padding: 0 10px;
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

.stat-item.low-battery.active {
  border-color: rgba(255, 122, 69, 0.4);
  background: rgba(255, 122, 69, 0.08);
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

.stat-item.low-battery .stat-value {
  color: #ff7a45;
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

.link-vda5050 {
  background: rgba(177, 102, 255, 0.16);
  color: #c792ff;
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
.ops-panel {
  padding: 8px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  max-height: 220px;
  overflow: auto;
}
.ops-line {
  font-size: 12px;
  color: #8b949e;
  padding: 2px 0;
}

/* V5-T2: SSE 连接状态指示器 */
.sse-status-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 16px;
  font-size: 12px;
  transition: background 0.3s, color 0.3s;
}

.sse-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.sse-status-text {
  font-weight: 600;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.sse-status-retry {
  font-size: 11px;
  opacity: 0.7;
}

.sse-connected {
  background: rgba(0, 230, 118, 0.08);
  color: #00e676;
}

.sse-connected .sse-status-dot {
  background: #00e676;
  box-shadow: 0 0 8px rgba(0, 230, 118, 0.6);
}

.sse-reconnecting {
  background: rgba(255, 202, 40, 0.08);
  color: #ffca28;
}

.sse-reconnecting .sse-status-dot {
  background: #ffca28;
  box-shadow: 0 0 8px rgba(255, 202, 40, 0.4);
  animation: sse-pulse 1.2s ease-in-out infinite;
}

.sse-disconnected {
  background: rgba(255, 61, 113, 0.08);
  color: #ff3d71;
}

.sse-disconnected .sse-status-dot {
  background: #ff3d71;
}

@keyframes sse-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.85); }
}

/* V5-N3: 预测性告警 */
.predictive-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.predictive-card {
  padding: 12px;
  border-radius: 10px;
  border: 1px solid var(--track-border);
  background: rgba(22, 27, 34, 0.45);
  cursor: pointer;
  transition: border-color 0.2s;

  &:hover {
    border-color: var(--track-border-accent);
  }

  &.trend-rapid_decline {
    border-left: 3px solid #ff4d4f;
  }

  &.trend-slight_decline {
    border-left: 3px solid #faad14;
  }

  &.trend-stable {
    border-left: 3px solid #52c41a;
  }
}

.predictive-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;

  strong {
    font-family: 'JetBrains Mono', monospace;
    font-size: 14px;
    color: var(--track-text);
  }
}

.predictive-soc {
  font-family: 'JetBrains Mono', monospace;
  font-size: 16px;
  font-weight: 700;
  color: #faad14;
}

.predictive-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.predictive-minutes {
  font-size: 12px;
  color: var(--track-text-muted);
}

.trend-indicator {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--track-text-muted);
}

.trend-detail {
  padding: 12px;
  border-radius: 8px;
  background: rgba(22, 27, 34, 0.45);
  font-size: 13px;
  color: var(--track-text-muted);

  p {
    margin: 4px 0;
  }
}
</style>
