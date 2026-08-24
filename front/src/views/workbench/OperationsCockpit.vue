<template>
  <main class="ops-cockpit">
    <section class="ops-hero">
      <div class="hero-copy">
        <h1>调度工作台</h1>
        <p>{{ parkScope.selectedParkName || '全部园区' }} · {{ lastUpdatedLabel }}</p>
      </div>
      <div class="hero-actions">
        <button type="button" class="ghost-action" :disabled="store.loading" @click="refresh">
          <ReloadOutlined :spin="store.loading" /> 刷新态势
        </button>
        <button
          v-if="authStore.canWrite"
          type="button"
          class="primary-action"
          @click="createOrderOpen = true"
        >
          <PlusOutlined /> 新建短驳订单
        </button>
      </div>
    </section>

    <section class="pulse-strip" aria-label="实时运营指标">
      <article v-for="metric in metrics" :key="metric.label" class="pulse-metric">
        <span class="pulse-value" :class="metric.tone">{{ metric.value }}</span>
        <span class="pulse-label">{{ metric.label }}</span>
        <span class="pulse-note">{{ metric.note }}</span>
      </article>
      <div class="pulse-status">
        <span class="live-dot"></span><span>{{ lastUpdatedLabel }}</span>
      </div>
    </section>

    <section class="command-grid">
      <div class="map-shell">
        <div class="map-toolbar">
          <div>
            <h2>送货范围与补能走廊</h2>
          </div>
          <div class="map-modes" role="tablist" aria-label="地图场景">
            <button
              v-for="mode in sceneModes"
              :key="mode.value"
              type="button"
              :class="{ active: activeScene === mode.value }"
              @click="activeScene = mode.value"
            >
              {{ mode.label }}
            </button>
          </div>
        </div>

        <div class="map-stage">
          <AmapGeoMap
            v-model:map-level="mapLevel"
            :center="mapCenter"
            :zoom="14"
            :markers="mapMarkers"
            :polygons="mapPolygons"
            :polylines="mapPolylines"
            :circles="L0_COVERAGE_CIRCLES"
            :fit-view-points="fitViewPoints"
            :fit-view-on-change="true"
            @marker-click="selectMapMarker"
          />
          <div class="map-legend">
            <span><i class="legend-dot vehicle"></i>车辆</span>
            <span><i class="legend-dot base"></i>找家纺网基地</span>
            <span><i class="legend-dot station"></i>服务点</span>
            <span><i class="legend-line service-range"></i>实际运营范围</span>
            <span><i class="legend-line delivery"></i>配送主线</span>
            <span><i class="legend-line charge"></i>回充走廊</span>
            <span><i class="legend-line service-range"></i>运营范围 5.5 × 6.2 km</span>
          </div>
          <section class="map-status-bar" aria-label="地图数据状态">
            <div class="status-item">
              <span class="status-label">坐标系</span>
              <span class="status-value">GCJ-02</span>
            </div>
            <div class="status-item">
              <span class="status-label">地图版本</span>
              <span class="status-value">{{ mapVersionLabel }}</span>
            </div>
            <div class="status-item">
              <span class="status-label">路线来源</span>
              <span class="status-value">{{ routeSourceLabel }}</span>
            </div>
            <div class="status-item">
              <span class="status-label">层级</span>
              <span class="status-value">{{ mapLevel }}</span>
            </div>
            <div class="status-item status-time">
              <span class="status-label">更新时间</span>
              <span class="status-value">{{ lastUpdatedLabel }}</span>
            </div>
          </section>
        </div>

        <div class="scene-rail">
          <button
            v-for="scene in visibleScenes"
            :key="scene.id"
            type="button"
            class="scene-card"
            :class="{ selected: selectedPlanId === scene.id }"
            @click="selectedPlanId = selectedPlanId === scene.id ? null : scene.id"
          >
            <span class="scene-color" :style="{ background: scene.color }"></span>
            <span class="scene-copy"
              ><strong>{{ scene.name }}</strong
              ><small>{{ scene.summary }}</small></span
            >
            <span class="scene-target">≤ {{ scene.targetMinutes }} min</span>
          </button>
        </div>
      </div>

      <aside class="decision-stack">
        <section class="decision-panel task-panel">
          <div class="panel-title-row">
            <div>
              <h2>待处理任务</h2>
            </div>
            <button type="button" class="text-link" @click="router.push('/tasks')">
              全部任务 →
            </button>
          </div>
          <div v-if="store.poolLoading && taskCards.length === 0" class="panel-empty">
            正在同步任务池…
          </div>
          <div v-else-if="taskCards.length === 0" class="panel-empty">当前没有待干预任务</div>
          <div v-else class="task-list">
            <article
              v-for="task in taskCards"
              :key="task.taskId"
              class="task-card"
              :class="{
                selected: selectedTaskId === task.taskId,
                'urgent-task':
                  (task.waitMinutes ?? 0) > 15 ||
                  task.orderPriority === 'P0' ||
                  task.orderPriority === 'P1',
              }"
              @click="selectedTaskId = task.taskId"
            >
              <div class="task-main">
                <span
                  class="task-priority"
                  :class="{
                    'p-high': task.orderPriority === 'P0' || task.orderPriority === 'P1',
                    'p-med': task.orderPriority === 'P2',
                  }"
                  >{{ task.orderPriority || 'P2' }}</span
                >
                <div>
                  <strong>{{ task.taskNo }}</strong>
                  <small :class="{ 'wait-warning': (task.waitMinutes ?? 0) > 15 }">
                    等待 {{ task.waitMinutes ?? 0 }} 分钟 ·
                    {{ taskStatusLabel(task.status) }}
                  </small>
                </div>
              </div>
              <div class="task-actions-inline">
                <button
                  v-if="authStore.canWrite"
                  type="button"
                  class="dispatch-button"
                  :disabled="dispatchingTaskId === task.taskId"
                  @click.stop="autoDispatch(task.taskId)"
                >
                  {{ dispatchingTaskId === task.taskId ? '派车中' : '自动派车' }}
                </button>
              </div>
            </article>
          </div>
        </section>

        <section class="decision-panel charge-panel">
          <div class="panel-title-row">
            <div>
              <h2>充电派送规则</h2>
            </div>
            <button type="button" class="text-link" @click="router.push('/analytics/charging')">
              充电报表 →
            </button>
          </div>
          <div class="soc-rule safe">
            <span class="soc-band">45–100%</span>
            <div><strong>正常派送</strong><small>允许跨分区任务，保留返航电量。</small></div>
            <b>{{ energyStats.ready }} 辆</b>
          </div>
          <div class="soc-rule watch">
            <span class="soc-band">25–45%</span>
            <div><strong>顺路补能</strong><small>只接短单，目的地优先靠近充电站。</small></div>
            <b>{{ energyStats.opportunity }} 辆</b>
          </div>
          <div class="soc-rule danger">
            <span class="soc-band">0–25%</span>
            <div><strong>强制回充</strong><small>退出派单池，分配最近空闲充电位。</small></div>
            <b>{{ energyStats.critical }} 辆</b>
          </div>
        </section>

        <section class="decision-panel exception-panel">
          <div class="panel-title-row">
            <div>
              <h2>需要人工介入</h2>
            </div>
            <button type="button" class="text-link" @click="router.push('/exceptions')">
              异常中心 →
            </button>
          </div>
          <button
            v-for="item in exceptionCards"
            :key="item.id"
            type="button"
            class="exception-row"
            @click="router.push(`/exceptions?id=${item.id}`)"
          >
            <span class="severity" :class="exceptionSeverity(item.exceptionType)"></span>
            <span
              ><strong>{{ item.exceptionMsg || item.exceptionType }}</strong
              ><small>任务 #{{ item.taskId || '—' }}</small></span
            >
            <ArrowRightOutlined />
          </button>
          <div v-if="exceptionCards.length === 0" class="panel-empty compact">无开放异常</div>
        </section>
      </aside>
    </section>

    <ParkDeliveryOrderModal
      v-model:open="createOrderOpen"
      :park-id="parkScope.selectedParkId"
      @created="refresh"
    />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ArrowRightOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import AmapGeoMap from '@/components/map/AmapGeoMap.vue'
import ParkDeliveryOrderModal from '@/components/park/ParkDeliveryOrderModal.vue'
import { useAuthStore } from '@/stores/auth'
import { useParkScopeStore } from '@/stores/parkScope'
import { useWorkbenchStore } from '@/stores/workbench'
import { getParkGeofences } from '@/api/park'
import { L0_COVERAGE_CIRCLES, vehicleToGeoPosition } from '@/composables/useDeliveryGeo'
import { buildGeofencePolygons, buildVehicleGeoMarkers } from '@/maps/parkGeoMapLayers'
import { filterGeoDeliverySimVehicles } from '@/maps/stationLayers'
import {
  DELIVERY_SCENE_PLANS,
  buildOperationsPlanPolylines,
  buildOperationsStationMarkers,
  type DeliverySceneId,
} from '@/maps/deliveryOperationsPlan'
import { ZJF_PILOT_GEO } from '@/maps/zjfPilotGeo'
import { isInsideZjfBase } from '@/maps/zjfStationAnchors'
import type { TaskStatus } from '@/constants/enums'
import type { GeoMapMarker } from '@/maps'
import type { ParkGeofence } from '@/types/park'

type SceneMode = 'delivery' | 'charging' | 'all'
type MapLevel = 'L0' | 'L1' | 'L2'

const router = useRouter()
const store = useWorkbenchStore()
const authStore = useAuthStore()
const parkScope = useParkScopeStore()

const activeScene = ref<SceneMode>('all')
const selectedPlanId = ref<DeliverySceneId | null>(null)
const selectedTaskId = ref<number | null>(null)
const dispatchingTaskId = ref<number | null>(null)
const createOrderOpen = ref(false)
const lastUpdatedAt = ref<Date | null>(null)
const mapLevel = ref<MapLevel>('L1')
const parkGeofences = ref<ParkGeofence[]>([])
const selectedMapMarkerId = ref<string | null>('operations-base')

const mapCenter: [number, number] = [ZJF_PILOT_GEO.anchorLng, ZJF_PILOT_GEO.anchorLat]
const sceneModes: Array<{ label: string; value: SceneMode }> = [
  { label: '全部', value: 'all' },
  { label: '配送', value: 'delivery' },
  { label: '充电', value: 'charging' },
]

const visibleScenes = computed(() =>
  DELIVERY_SCENE_PLANS.filter(
    (scene) => activeScene.value === 'all' || scene.kind === activeScene.value,
  ),
)

const operationalVehicles = computed(() => filterGeoDeliverySimVehicles(store.parkVehicles))
const baseVehicles = computed(() =>
  operationalVehicles.value.filter((vehicle) => isInsideZjfBase(vehicleToGeoPosition(vehicle))),
)
const roadVehicles = computed(() =>
  operationalVehicles.value.filter((vehicle) => !isInsideZjfBase(vehicleToGeoPosition(vehicle))),
)
const stationMarkers = computed(() => buildOperationsStationMarkers(baseVehicles.value.length))
const selectedVehicleId = computed(() => {
  if (!selectedMapMarkerId.value || selectedMapMarkerId.value.startsWith('operations-station-')) {
    return null
  }
  const id = Number(selectedMapMarkerId.value)
  return Number.isFinite(id) ? id : null
})
const mapMarkers = computed(() => [
  ...stationMarkers.value.map((marker) => {
    const selected = marker.id === selectedMapMarkerId.value
    return { ...marker, selected, showLabel: selected }
  }),
  ...buildVehicleGeoMarkers(roadVehicles.value, { selectedId: selectedVehicleId.value }),
])
const mapPolygons = computed(() => buildGeofencePolygons(parkGeofences.value))
const mapPolylines = computed(() => {
  if (selectedPlanId.value) return buildOperationsPlanPolylines(selectedPlanId.value)
  return visibleScenes.value.flatMap((scene) => buildOperationsPlanPolylines(scene.id))
})
const fitViewPoints = computed<[number, number][]>(() => {
  if (selectedPlanId.value) return mapPolylines.value.flatMap((line) => line.path)
  const serviceEnvelope = parkGeofences.value.find(
    (fence) =>
      fence.scopeCode === 'L1_CANDIDATE_ENVELOPE' && fence.fenceCode === 'DEFAULT-BOUNDARY',
  )
  const servicePolygon = serviceEnvelope?.polygon ?? []
  if (servicePolygon.length >= 3) {
    return servicePolygon.map((point) => [Number(point[0]), Number(point[1])])
  }
  const vehiclePoints = operationalVehicles.value.slice(0, 10).map(vehicleToGeoPosition)
  return vehiclePoints.length > 1 ? vehiclePoints : []
})

const metrics = computed(() => [
  { label: '待派任务', value: store.pendingCount, note: '进入实时决策池', tone: 'cyan' },
  { label: '人工介入', value: store.manualPendingCount, note: '需调度员确认', tone: 'amber' },
  { label: '开放异常', value: store.openExceptionCount, note: '阻塞履约风险', tone: 'rose' },
  {
    label: '可派车辆',
    value: store.assignableVehicleCount,
    note: '满足在线与电量约束',
    tone: 'green',
  },
  { label: '充电中', value: store.chargingCount, note: '正在恢复运力', tone: 'neutral' },
])
const taskCards = computed(() => store.taskPool.slice(0, 5))
const exceptionCards = computed(() => store.openExceptions.slice(0, 4))
const energyStats = computed(() => {
  const online = operationalVehicles.value.filter((vehicle) => vehicle.onlineStatus !== 'OFFLINE')
  return {
    ready: online.filter((vehicle) => vehicle.batteryLevel >= 45).length,
    opportunity: online.filter((vehicle) => vehicle.batteryLevel >= 25 && vehicle.batteryLevel < 45)
      .length,
    critical: online.filter((vehicle) => vehicle.batteryLevel < 25).length,
  }
})
const lastUpdatedLabel = computed(() => {
  if (!lastUpdatedAt.value) return '等待首次同步'
  return `态势更新于 ${lastUpdatedAt.value.toLocaleTimeString('zh-CN', { hour12: false })}`
})
const mapVersionLabel = computed(() => 'V44 · 2026-07-18')
const routeSourceLabel = computed(() => {
  if (selectedPlanId.value) return 'LOCAL_GRAPH'
  return 'AMAP + LOCAL_GRAPH'
})

function taskStatusLabel(status: TaskStatus | string) {
  const labels: Record<string, string> = {
    PENDING: '待派车',
    MANUAL_PENDING: '待人工派车',
    ASSIGNED: '已分配',
    EXECUTING: '执行中',
    FAILED: '派车失败',
  }
  return labels[String(status)] || String(status)
}

function exceptionSeverity(type: string) {
  return type === 'VEHICLE_OFFLINE' || type === 'TASK_EXECUTE_FAILED' ? 'critical' : 'warning'
}

async function refresh() {
  const [fenceResponse] = await Promise.all([
    getParkGeofences(parkScope.selectedParkId),
    store.fetchQueue(),
  ])
  parkGeofences.value = fenceResponse.data || []
  lastUpdatedAt.value = new Date()
}

function selectMapMarker(marker: GeoMapMarker) {
  selectedMapMarkerId.value = selectedMapMarkerId.value === marker.id ? null : marker.id
}

async function autoDispatch(taskId: number) {
  if (!authStore.canWrite || dispatchingTaskId.value) return
  dispatchingTaskId.value = taskId
  try {
    await store.dispatchAuto(taskId)
    message.success('自动派车完成')
    lastUpdatedAt.value = new Date()
  } catch {
    message.error('自动派车失败，请查看异常原因')
  } finally {
    dispatchingTaskId.value = null
  }
}

watch(activeScene, () => {
  selectedPlanId.value = null
})
watch(
  () => parkScope.selectedParkId,
  () => refresh(),
)
onMounted(() => refresh())
</script>

<style scoped lang="less">
.ops-cockpit {
  --ops-bg: var(--fsd-surface-workspace);
  --ops-panel: var(--fsd-surface-raised);
  --ops-border: var(--fsd-border);
  --ops-text: var(--fsd-text-primary);
  --ops-muted: var(--fsd-text-secondary);
  --ops-cyan: var(--fsd-accent);
  min-height: 100%;
  padding: var(--fsd-space-5);
  color: var(--ops-text);
  background: var(--ops-bg);
}

button {
  font: inherit;
}
.ops-hero,
.pulse-strip,
.command-grid,
.operating-principles {
  max-width: 1680px;
  margin-inline: auto;
}
.ops-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--fsd-space-4);
  padding: 0 0 var(--fsd-space-4);
}
.hero-copy h1 {
  margin: 0;
  color: var(--fsd-text-primary);
  font-size: var(--fsd-text-xl);
  font-weight: var(--fsd-font-semibold);
  letter-spacing: var(--fsd-tracking-tight);
  line-height: var(--fsd-leading-tight);
}
.hero-copy p {
  margin: var(--fsd-space-1) 0 0;
  color: var(--ops-muted);
  font-size: var(--fsd-text-xs);
}
.hero-actions {
  display: flex;
  gap: var(--fsd-space-2);
}
.ghost-action,
.primary-action,
.dispatch-button {
  min-height: var(--fsd-touch-target-min);
  border: 1px solid var(--ops-border);
  border-radius: var(--fsd-radius-sm);
  padding: 9px 14px;
  cursor: pointer;
  transition:
    background-color var(--fsd-transition-base),
    border-color var(--fsd-transition-base),
    color var(--fsd-transition-base);
}
.ghost-action {
  color: var(--ops-text);
  background: var(--fsd-surface-raised);
}
.primary-action {
  color: var(--fsd-text-on-action);
  border-color: transparent;
  background: var(--fsd-action-primary);
  font-weight: var(--fsd-font-semibold);
}
.ghost-action:hover {
  border-color: var(--fsd-border-active);
  background: var(--fsd-bg-hover);
}
.primary-action:hover {
  background: var(--fsd-action-primary-hover);
}
.ghost-action:focus-visible,
.primary-action:focus-visible,
.dispatch-button:focus-visible {
  outline: 2px solid var(--fsd-accent-strong);
  outline-offset: 2px;
}
.primary-action:disabled,
.ghost-action:disabled,
.dispatch-button:disabled {
  cursor: not-allowed;
  opacity: 0.56;
}

.pulse-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(110px, 1fr)) auto;
  border-block: 1px solid var(--ops-border);
}
.pulse-metric {
  min-width: 0;
  padding: var(--fsd-space-3) var(--fsd-space-4);
  border-right: 1px solid var(--ops-border);
}
.pulse-value {
  display: block;
  color: var(--ops-text);
  font-family: var(--fsd-font-mono);
  font-size: var(--fsd-text-xl);
  font-weight: var(--fsd-font-semibold);
  line-height: var(--fsd-leading-tight);
}
.pulse-value.cyan {
  color: var(--fsd-accent);
}
.pulse-value.amber {
  color: var(--fsd-warning);
}
.pulse-value.rose {
  color: var(--fsd-error);
}
.pulse-value.green {
  color: var(--fsd-success);
}
.pulse-label {
  display: block;
  margin-top: var(--fsd-space-1);
  font-weight: var(--fsd-font-medium);
}
.pulse-note {
  display: block;
  margin-top: 2px;
  color: var(--ops-muted);
  font-size: var(--fsd-text-xs);
}
.pulse-status {
  display: flex;
  align-items: center;
  gap: var(--fsd-space-2);
  padding: 0 var(--fsd-space-4);
  color: var(--ops-muted);
  font-size: var(--fsd-text-xs);
  white-space: nowrap;
}
.live-dot {
  width: 7px;
  height: 7px;
  border-radius: var(--fsd-radius-full);
  background: var(--fsd-accent);
}

.command-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(340px, 0.75fr);
  gap: var(--fsd-space-4);
  margin-top: var(--fsd-space-4);
}
.map-shell {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--ops-border);
  border-radius: var(--fsd-radius-md);
  background: var(--ops-panel);
}
.map-toolbar,
.panel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.map-toolbar {
  padding: var(--fsd-space-4);
}
.map-toolbar h2,
.panel-title-row h2 {
  margin: 0;
  color: var(--fsd-text-primary);
  font-size: var(--fsd-text-md);
  font-weight: var(--fsd-font-semibold);
}
.map-modes {
  display: flex;
  gap: 2px;
  padding: 2px;
  border: 1px solid var(--ops-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-surface-status);
}
.map-modes button {
  min-height: 32px;
  border: 0;
  border-radius: var(--fsd-radius-sm);
  padding: 6px 10px;
  color: var(--ops-muted);
  background: transparent;
  cursor: pointer;
}
.map-modes button.active {
  color: var(--fsd-text-on-action);
  background: var(--fsd-action-primary);
  font-weight: var(--fsd-font-semibold);
}
.map-stage {
  position: relative;
  height: 610px;
  border-block: 1px solid var(--ops-border);
  background: var(--fsd-surface-page);
}
.map-stage :deep(.amap-geo-map) {
  height: 100%;
}
.map-legend {
  position: absolute;
  z-index: 5;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-surface-overlay);
  box-shadow: var(--fsd-shadow-popover);
}
.map-legend {
  left: var(--fsd-space-3);
  bottom: 46px;
  display: flex;
  max-width: calc(100% - 2 * var(--fsd-space-3));
  flex-wrap: wrap;
  gap: var(--fsd-space-2);
  padding: var(--fsd-space-2) var(--fsd-space-3);
  color: var(--fsd-text-secondary);
  font-size: var(--fsd-text-xs);
}
.map-legend span {
  display: flex;
  align-items: center;
  gap: var(--fsd-space-1);
}
.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: var(--fsd-radius-full);
}
.legend-dot.vehicle {
  background: var(--fsd-accent);
}
.legend-dot.station {
  background: var(--fsd-text-primary);
}
.legend-dot.base {
  background: var(--fsd-warning);
}
.legend-line {
  width: 18px;
  height: 2px;
}
.legend-line.delivery {
  background: var(--fsd-warning);
}
.legend-line.charge {
  border-top: 2px dashed var(--fsd-error);
}
.legend-line.service-range {
  height: 0;
  border-top: 2px dashed var(--fsd-accent);
}

.scene-rail {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border-top: 1px solid var(--ops-border);
}
.scene-card {
  display: grid;
  grid-template-columns: 4px 1fr auto;
  gap: var(--fsd-space-3);
  align-items: center;
  min-height: 104px;
  padding: var(--fsd-space-3);
  text-align: left;
  color: inherit;
  border: 0;
  border-right: 1px solid var(--ops-border);
  background: var(--ops-panel);
  cursor: pointer;
}
.scene-card:hover {
  background: var(--fsd-bg-hover);
}
.scene-card.selected {
  background: var(--fsd-accent-selected);
}
.scene-color {
  width: 4px;
  height: 54px;
  border-radius: var(--fsd-radius-sm);
}
.scene-copy {
  display: grid;
  gap: 7px;
}
.scene-copy small {
  color: var(--ops-muted);
  line-height: 1.45;
}
.scene-target {
  color: var(--fsd-text-secondary);
  font:
    700 11px 'Geist Mono',
    monospace;
}

.decision-stack {
  display: grid;
  gap: var(--fsd-space-4);
  align-content: start;
}
.decision-panel {
  padding: 0 0 var(--fsd-space-4);
  border-bottom: 1px solid var(--ops-border);
}
.text-link {
  min-height: var(--fsd-touch-target-min);
  padding: 0;
  color: var(--ops-muted);
  border: 0;
  background: none;
  cursor: pointer;
  font-size: var(--fsd-text-xs);
}
.text-link:hover {
  color: var(--fsd-accent);
}
.text-link:focus-visible {
  outline: 2px solid var(--fsd-accent-strong);
  outline-offset: 2px;
}
.task-list {
  margin-top: var(--fsd-space-2);
  border-top: 1px solid var(--ops-border);
}
.task-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--fsd-space-3);
  min-height: var(--fsd-touch-target-min);
  padding: var(--fsd-space-3) var(--fsd-space-2);
  border: 0;
  border-bottom: 1px solid var(--ops-border);
  background: transparent;
  cursor: pointer;
}
.task-card:hover {
  background: var(--fsd-bg-hover);
}
.task-card.selected {
  background: var(--fsd-accent-selected);
}
.task-main {
  display: flex;
  align-items: center;
  gap: var(--fsd-space-2);
  min-width: 0;
}
.task-priority {
  padding: 3px 6px;
  border-radius: var(--fsd-radius-sm);
  color: var(--fsd-text-secondary);
  background: var(--fsd-neutral-bg);
  font-family: var(--fsd-font-mono);
  font-size: var(--fsd-text-xs);
  font-weight: var(--fsd-font-semibold);
}
.task-priority.p-high {
  color: var(--fsd-error);
  background: var(--fsd-error-bg);
}
.task-priority.p-med {
  color: var(--fsd-warning);
  background: var(--fsd-warning-bg);
}
.task-card.urgent-task {
  border-left: 3px solid var(--fsd-error);
}
.wait-warning {
  color: var(--fsd-error) !important;
  font-weight: 600;
}
.task-main div {
  display: grid;
  gap: 4px;
  min-width: 0;
}
.task-main strong {
  overflow: hidden;
  text-overflow: ellipsis;
}
.task-main small {
  color: var(--ops-muted);
}
.dispatch-button {
  flex: 0 0 auto;
  min-height: 32px;
  padding: 5px 9px;
  color: var(--fsd-text-on-action);
  background: var(--fsd-action-primary);
  border-color: transparent;
  font-size: var(--fsd-text-xs);
}
.dispatch-button:disabled {
  opacity: 0.56;
  cursor: wait;
}
.panel-empty {
  margin-top: var(--fsd-space-2);
  padding: var(--fsd-space-5) var(--fsd-space-3);
  color: var(--ops-muted);
  text-align: center;
  border-bottom: 1px solid var(--ops-border);
}
.panel-empty.compact {
  padding: 16px 8px;
}

.soc-rule {
  display: grid;
  grid-template-columns: 72px 1fr auto;
  gap: var(--fsd-space-3);
  align-items: center;
  padding: var(--fsd-space-3) 0;
  border-bottom: 1px solid var(--ops-border);
}
.soc-rule div {
  display: grid;
  gap: 3px;
}
.soc-rule small {
  color: var(--ops-muted);
  line-height: 1.4;
}
.soc-rule b {
  font:
    700 12px 'Geist Mono',
    monospace;
}
.soc-band {
  font:
    700 11px 'Geist Mono',
    monospace;
}
.soc-rule.safe .soc-band {
  color: var(--fsd-success);
}
.soc-rule.watch .soc-band {
  color: var(--fsd-warning);
}
.soc-rule.danger .soc-band {
  color: var(--fsd-error);
}

.exception-row {
  width: 100%;
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--fsd-space-2);
  min-height: var(--fsd-touch-target-min);
  padding: var(--fsd-space-3) 0;
  color: inherit;
  text-align: left;
  border: 0;
  border-bottom: 1px solid var(--ops-border);
  background: transparent;
  cursor: pointer;
}
.exception-row span:nth-child(2) {
  display: grid;
  gap: 3px;
}
.exception-row small {
  color: var(--ops-muted);
}
.severity {
  width: 7px;
  height: 7px;
  border-radius: var(--fsd-radius-full);
  background: var(--fsd-warning);
}
.severity.critical,
.severity.error {
  background: var(--fsd-error);
}

.map-status-bar {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 4;
  display: flex;
  min-height: 34px;
  overflow-x: auto;
  border-top: 1px solid var(--fsd-border);
  background: var(--fsd-surface-overlay);
}
.status-item {
  display: inline-flex;
  align-items: center;
  gap: var(--fsd-space-1);
  min-width: max-content;
  padding: 8px var(--fsd-space-3);
  border-right: 1px solid var(--fsd-border-split);
}
.status-item.status-time {
  margin-left: auto;
}
.status-label {
  color: var(--ops-muted);
  font-family: var(--fsd-font-mono);
  font-size: 10px;
  font-weight: var(--fsd-font-medium);
}
.status-value {
  color: var(--ops-text);
  font-family: var(--fsd-font-mono);
  font-size: 11px;
  font-weight: var(--fsd-font-medium);
}

@media (max-width: 1280px) {
  .command-grid {
    grid-template-columns: 1fr;
  }
  .decision-stack {
    grid-template-columns: repeat(3, 1fr);
  }
  .decision-panel {
    min-width: 0;
  }
  .map-stage {
    height: 520px;
  }
}

@media (max-width: 900px) {
  .ops-cockpit {
    padding: var(--fsd-space-4);
  }
  .ops-hero {
    align-items: flex-start;
    flex-direction: column;
  }
  .pulse-strip {
    grid-template-columns: repeat(2, 1fr);
  }
  .pulse-status {
    min-height: 54px;
  }
  .decision-stack,
  .scene-rail {
    grid-template-columns: 1fr;
  }
  .status-item {
    min-width: 120px;
  }
  .map-stage {
    height: 460px;
  }
  .scene-card {
    border-right: 0;
    border-bottom: 1px solid var(--ops-border);
  }
}

@media (max-width: 560px) {
  .hero-actions {
    width: 100%;
  }
  .hero-actions button {
    flex: 1;
  }
  .pulse-strip {
    grid-template-columns: 1fr 1fr;
  }
  .pulse-metric {
    padding: var(--fsd-space-3);
  }
  .pulse-status {
    grid-column: 1 / -1;
  }
  .map-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
  .map-stage {
    height: 400px;
  }
  .map-legend {
    right: var(--fsd-space-3);
    bottom: 46px;
  }
  .status-item.status-time {
    margin-left: 0;
  }
}
</style>
