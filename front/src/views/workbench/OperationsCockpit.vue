<template>
  <main class="ops-cockpit">
    <section class="ops-hero">
      <div class="hero-copy">
        <span class="eyebrow">DIESHIQIAO · LIVE OPERATIONS</span>
        <h1>把每一次派送，放回真实道路。</h1>
        <p>围绕任务、车辆、送货范围与补能约束做决策；历史报表和基础配置退到二级页面。</p>
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
            <span class="section-kicker">REAL MAP</span>
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
            :polygons="PILOT_BOUNDARY_POLYGON"
            :polylines="mapPolylines"
            :circles="L0_COVERAGE_CIRCLES"
            :fit-view-points="fitViewPoints"
          />
          <div class="map-legend">
            <span><i class="legend-dot vehicle"></i>车辆</span>
            <span><i class="legend-dot station"></i>站点</span>
            <span><i class="legend-line delivery"></i>配送主线</span>
            <span><i class="legend-line charge"></i>回充走廊</span>
          </div>
          <div class="coverage-card">
            <span>试点有效范围</span><strong>1.57 × 0.47 km</strong
            ><small>五分区围栏 · 超界即停派</small>
          </div>
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
              <span class="section-kicker">NEXT ACTION</span>
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
              :class="{ selected: selectedTaskId === task.taskId }"
              @click="selectedTaskId = task.taskId"
            >
              <div class="task-main">
                <span class="task-priority">{{ task.orderPriority || 'P2' }}</span>
                <div>
                  <strong>{{ task.taskNo }}</strong
                  ><small
                    >等待 {{ task.waitMinutes ?? 0 }} 分钟 ·
                    {{ taskStatusLabel(task.status) }}</small
                  >
                </div>
              </div>
              <button
                v-if="authStore.canWrite"
                type="button"
                class="dispatch-button"
                :disabled="dispatchingTaskId === task.taskId"
                @click.stop="autoDispatch(task.taskId)"
              >
                {{ dispatchingTaskId === task.taskId ? '派车中' : '自动派车' }}
              </button>
            </article>
          </div>
        </section>

        <section class="decision-panel charge-panel">
          <div class="panel-title-row">
            <div>
              <span class="section-kicker">ENERGY POLICY</span>
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
              <span class="section-kicker">EXCEPTIONS</span>
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
import {
  L0_COVERAGE_CIRCLES,
  PILOT_BOUNDARY_POLYGON,
  buildVehicleGeoMarkers,
  vehicleToGeoPosition,
} from '@/composables/useDeliveryGeo'
import {
  DELIVERY_SCENE_PLANS,
  buildOperationsPlanPolylines,
  buildOperationsStationMarkers,
  type DeliverySceneId,
} from '@/maps/deliveryOperationsPlan'
import { ZJF_PILOT_GEO } from '@/maps/zjfPilotGeo'
import type { TaskStatus } from '@/constants/enums'

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

const mapCenter: [number, number] = [ZJF_PILOT_GEO.anchorLng, ZJF_PILOT_GEO.anchorLat]
const stationMarkers = buildOperationsStationMarkers()
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

const mapMarkers = computed(() => [
  ...stationMarkers,
  ...buildVehicleGeoMarkers(store.parkVehicles),
])
const mapPolylines = computed(() => {
  if (selectedPlanId.value) return buildOperationsPlanPolylines(selectedPlanId.value)
  return visibleScenes.value.flatMap((scene) => buildOperationsPlanPolylines(scene.id))
})
const fitViewPoints = computed<[number, number][]>(() => {
  if (selectedPlanId.value) return mapPolylines.value.flatMap((line) => line.path)
  const vehiclePoints = store.parkVehicles.slice(0, 10).map(vehicleToGeoPosition)
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
  { label: '充电中', value: store.chargingCount, note: '正在恢复运力', tone: 'violet' },
])
const taskCards = computed(() => store.taskPool.slice(0, 5))
const exceptionCards = computed(() => store.openExceptions.slice(0, 4))
const energyStats = computed(() => {
  const online = store.parkVehicles.filter((vehicle) => vehicle.onlineStatus !== 'OFFLINE')
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
const mapVersionLabel = computed(() => 'V43 · 2026-07-18')
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
  await store.fetchQueue()
  lastUpdatedAt.value = new Date()
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
  --ops-bg: #070b0f;
  --ops-panel: rgba(13, 20, 27, 0.88);
  --ops-border: rgba(151, 178, 199, 0.16);
  --ops-text: #edf7fb;
  --ops-muted: #81929f;
  --ops-cyan: #22d3ee;
  min-height: 100%;
  padding: 28px;
  color: var(--ops-text);
  background:
    linear-gradient(rgba(34, 211, 238, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(34, 211, 238, 0.035) 1px, transparent 1px),
    radial-gradient(circle at 68% 10%, rgba(20, 184, 166, 0.15), transparent 28%),
    radial-gradient(circle at 10% 40%, rgba(245, 158, 11, 0.08), transparent 24%), var(--ops-bg);
  background-size:
    40px 40px,
    40px 40px,
    auto,
    auto,
    auto;
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
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 18px 4px 26px;
}
.eyebrow,
.section-kicker {
  color: var(--ops-cyan);
  font-family: 'Geist Mono', monospace;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
}
.hero-copy h1 {
  max-width: 820px;
  margin: 12px 0 10px;
  font-family: 'Plus Jakarta Sans', sans-serif;
  font-size: clamp(32px, 4vw, 64px);
  line-height: 1.02;
  letter-spacing: -0.05em;
}
.hero-copy p {
  max-width: 720px;
  margin: 0;
  color: var(--ops-muted);
  font-size: 15px;
}
.hero-actions {
  display: flex;
  gap: 10px;
}
.ghost-action,
.primary-action,
.dispatch-button {
  border: 1px solid var(--ops-border);
  border-radius: 10px;
  padding: 11px 16px;
  cursor: pointer;
  transition: 160ms ease;
}
.ghost-action {
  color: var(--ops-text);
  background: rgba(13, 20, 27, 0.72);
}
.primary-action {
  color: #021116;
  border-color: transparent;
  background: var(--ops-cyan);
  font-weight: 800;
}
.ghost-action:hover {
  border-color: rgba(34, 211, 238, 0.55);
}
.primary-action:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 30px rgba(34, 211, 238, 0.22);
}

.pulse-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(110px, 1fr)) auto;
  border: 1px solid var(--ops-border);
  border-radius: 14px;
  background: rgba(7, 12, 17, 0.78);
  overflow: hidden;
}
.pulse-metric {
  padding: 18px 20px;
  border-right: 1px solid var(--ops-border);
}
.pulse-value {
  display: block;
  font:
    700 28px/1 'Geist Mono',
    monospace;
}
.pulse-value.cyan {
  color: #22d3ee;
}
.pulse-value.amber {
  color: #fbbf24;
}
.pulse-value.rose {
  color: #fb7185;
}
.pulse-value.green {
  color: #4ade80;
}
.pulse-value.violet {
  color: #c4b5fd;
}
.pulse-label {
  display: block;
  margin-top: 8px;
  font-weight: 700;
}
.pulse-note {
  display: block;
  margin-top: 4px;
  color: var(--ops-muted);
  font-size: 11px;
}
.pulse-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 20px;
  color: var(--ops-muted);
  font-size: 12px;
  white-space: nowrap;
}
.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #4ade80;
  box-shadow: 0 0 0 5px rgba(74, 222, 128, 0.08);
}

.command-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(360px, 0.75fr);
  gap: 18px;
  margin-top: 18px;
}
.map-shell,
.decision-panel,
.operating-principles {
  border: 1px solid var(--ops-border);
  background: var(--ops-panel);
  box-shadow: 0 26px 80px rgba(0, 0, 0, 0.18);
}
.map-shell {
  min-width: 0;
  border-radius: 18px;
  overflow: hidden;
}
.map-toolbar,
.panel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.map-toolbar {
  padding: 20px 22px 16px;
}
.map-toolbar h2,
.panel-title-row h2,
.operating-principles h2 {
  margin: 5px 0 0;
  font-size: 18px;
}
.map-modes {
  display: flex;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--ops-border);
  border-radius: 10px;
  background: #080d12;
}
.map-modes button {
  border: 0;
  border-radius: 7px;
  padding: 7px 12px;
  color: var(--ops-muted);
  background: transparent;
  cursor: pointer;
}
.map-modes button.active {
  color: #031014;
  background: var(--ops-cyan);
  font-weight: 800;
}
.map-stage {
  position: relative;
  height: 610px;
  border-block: 1px solid var(--ops-border);
  background: #06090d;
}
.map-stage :deep(.amap-geo-map) {
  height: 100%;
}
.map-legend,
.coverage-card {
  position: absolute;
  z-index: 5;
  backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(5, 11, 16, 0.8);
}
.map-legend {
  left: 16px;
  bottom: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 11px;
  color: #b8c7d1;
}
.map-legend span {
  display: flex;
  align-items: center;
  gap: 6px;
}
.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.legend-dot.vehicle {
  background: #22d3ee;
}
.legend-dot.station {
  background: #f8fafc;
}
.legend-line {
  width: 18px;
  height: 2px;
}
.legend-line.delivery {
  background: #fbbf24;
}
.legend-line.charge {
  border-top: 2px dashed #fb7185;
}
.coverage-card {
  right: 16px;
  bottom: 16px;
  display: grid;
  gap: 3px;
  padding: 12px 14px;
  border-radius: 10px;
}
.coverage-card span,
.coverage-card small {
  color: var(--ops-muted);
  font-size: 10px;
}
.coverage-card strong {
  font:
    700 16px 'Geist Mono',
    monospace;
}

.scene-rail {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: var(--ops-border);
}
.scene-card {
  display: grid;
  grid-template-columns: 4px 1fr auto;
  gap: 12px;
  align-items: center;
  min-height: 112px;
  padding: 16px;
  text-align: left;
  color: inherit;
  border: 0;
  background: #0c1319;
  cursor: pointer;
}
.scene-card:hover,
.scene-card.selected {
  background: #121c24;
}
.scene-color {
  width: 4px;
  height: 54px;
  border-radius: 999px;
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
  color: #d7e6ee;
  font:
    700 11px 'Geist Mono',
    monospace;
}

.decision-stack {
  display: grid;
  gap: 18px;
  align-content: start;
}
.decision-panel {
  border-radius: 16px;
  padding: 18px;
}
.text-link {
  padding: 0;
  color: var(--ops-muted);
  border: 0;
  background: none;
  cursor: pointer;
  font-size: 12px;
}
.text-link:hover {
  color: var(--ops-cyan);
}
.task-list {
  display: grid;
  gap: 8px;
  margin-top: 16px;
}
.task-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 11px;
  background: #0a1016;
  cursor: pointer;
}
.task-card:hover,
.task-card.selected {
  border-color: rgba(34, 211, 238, 0.3);
  background: rgba(34, 211, 238, 0.055);
}
.task-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.task-priority {
  padding: 4px 6px;
  border-radius: 6px;
  color: #fbbf24;
  background: rgba(251, 191, 36, 0.08);
  font:
    700 10px 'Geist Mono',
    monospace;
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
  padding: 7px 10px;
  color: #baf6ff;
  background: rgba(34, 211, 238, 0.08);
  border-color: rgba(34, 211, 238, 0.2);
  font-size: 11px;
}
.dispatch-button:disabled {
  opacity: 0.5;
  cursor: wait;
}
.panel-empty {
  margin-top: 16px;
  padding: 26px 12px;
  color: var(--ops-muted);
  text-align: center;
  border: 1px dashed var(--ops-border);
  border-radius: 10px;
}
.panel-empty.compact {
  padding: 16px 8px;
}

.charge-panel {
  background: linear-gradient(145deg, rgba(13, 20, 27, 0.94), rgba(22, 18, 12, 0.94));
}
.soc-rule {
  display: grid;
  grid-template-columns: 72px 1fr auto;
  gap: 12px;
  align-items: center;
  margin-top: 10px;
  padding: 12px;
  border-radius: 11px;
  border: 1px solid var(--ops-border);
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
  color: #4ade80;
}
.soc-rule.watch .soc-band {
  color: #fbbf24;
}
.soc-rule.danger .soc-band {
  color: #fb7185;
}

.exception-row {
  width: 100%;
  display: grid;
  grid-template-columns: 8px 1fr auto;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  padding: 11px 0;
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
  border-radius: 50%;
  background: #fbbf24;
}
.severity.critical,
.severity.error {
  background: #fb7185;
  box-shadow: 0 0 0 4px rgba(251, 113, 133, 0.08);
}

.map-status-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 1px;
  margin-top: 18px;
  border: 1px solid var(--ops-border);
  border-radius: 14px;
  overflow: hidden;
  background: var(--ops-border);
}
.status-item {
  display: grid;
  gap: 4px;
  padding: 12px 16px;
  background: #0b1117;
  flex: 1 1 auto;
  min-width: 140px;
}
.status-item.status-time {
  flex: 1.4 1 180px;
}
.status-label {
  color: var(--ops-muted);
  font:
    700 10px 'Geist Mono',
    monospace;
  letter-spacing: 0.16em;
}
.status-value {
  color: var(--ops-text);
  font:
    600 13px 'Geist Mono',
    monospace;
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
    padding: 14px;
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
  .coverage-card {
    display: none;
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
    padding: 14px;
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
    right: 12px;
    bottom: 12px;
  }
}
</style>
