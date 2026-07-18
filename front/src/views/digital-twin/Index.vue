<template>
  <PageContainer title="数字孪生" subtitle="3D 风格园区态势 · 实时车辆叠加 · 调度方案预评估">
    <template #actions>
      <a-space>
        <a-radio-group v-model:value="mode" button-style="solid" size="small">
          <a-radio-button value="SINGLE">单场景</a-radio-button>
          <a-radio-button value="COMPARE">A/B 对比</a-radio-button>
        </a-radio-group>
      </a-space>
    </template>

    <!-- Single Scenario Mode -->
    <template v-if="mode === 'SINGLE'">
      <div class="scenario-controls">
        <a-space>
          <a-select
            v-model:value="scenario"
            style="width: 200px"
            :options="scenarioOptions"
          />
          <a-input-number v-model:value="pendingTaskCount" :min="1" :max="200" />
          <a-button type="primary" :loading="simulating" @click="runSimulate">仿真推演</a-button>
          <a-button :loading="loading" @click="load">刷新</a-button>
        </a-space>
      </div>
      <div class="params-row">
        <div class="param-item">
          <span class="param-label">车速</span>
          <a-slider
            v-model:value="params.speed"
            :min="0.5"
            :max="2.0"
            :step="0.1"
            style="width: 140px"
          />
          <span class="param-value">{{ params.speed.toFixed(1) }}x</span>
        </div>
        <div class="param-item">
          <span class="param-label">发单频率</span>
          <a-slider
            v-model:value="params.orderInterval"
            :min="1"
            :max="20"
            :step="1"
            style="width: 140px"
          />
          <span class="param-value">{{ params.orderInterval }} min</span>
        </div>
        <div class="param-item">
          <span class="param-label">车辆数量</span>
          <a-slider
            v-model:value="params.vehicleCount"
            :min="1"
            :max="50"
            :step="1"
            style="width: 140px"
          />
          <span class="param-value">{{ params.vehicleCount }} 台</span>
        </div>
      </div>
    </template>

    <!-- A/B Compare Mode -->
    <template v-if="mode === 'COMPARE'">
      <div class="compare-panels">
        <div class="compare-panel panel-a">
          <h4 class="panel-title">场景 A</h4>
          <a-select
            v-model:value="compareA.scenario"
            style="width: 100%"
            :options="scenarioOptions"
          />
          <div class="param-item">
            <span class="param-label">车速</span>
            <a-slider
              v-model:value="compareA.params.speed"
              :min="0.5"
              :max="2.0"
              :step="0.1"
              style="width: 100px"
            />
            <span class="param-value">{{ compareA.params.speed.toFixed(1) }}x</span>
          </div>
          <div class="param-item">
            <span class="param-label">发单频率</span>
            <a-slider
              v-model:value="compareA.params.orderInterval"
              :min="1"
              :max="20"
              :step="1"
              style="width: 100px"
            />
            <span class="param-value">{{ compareA.params.orderInterval }} min</span>
          </div>
          <div class="param-item">
            <span class="param-label">车辆数量</span>
            <a-slider
              v-model:value="compareA.params.vehicleCount"
              :min="1"
              :max="50"
              :step="1"
              style="width: 100px"
            />
            <span class="param-value">{{ compareA.params.vehicleCount }} 台</span>
          </div>
        </div>
        <div class="compare-panel panel-b">
          <h4 class="panel-title">场景 B</h4>
          <a-select
            v-model:value="compareB.scenario"
            style="width: 100%"
            :options="scenarioOptions"
          />
          <div class="param-item">
            <span class="param-label">车速</span>
            <a-slider
              v-model:value="compareB.params.speed"
              :min="0.5"
              :max="2.0"
              :step="0.1"
              style="width: 100px"
            />
            <span class="param-value">{{ compareB.params.speed.toFixed(1) }}x</span>
          </div>
          <div class="param-item">
            <span class="param-label">发单频率</span>
            <a-slider
              v-model:value="compareB.params.orderInterval"
              :min="1"
              :max="20"
              :step="1"
              style="width: 100px"
            />
            <span class="param-value">{{ compareB.params.orderInterval }} min</span>
          </div>
          <div class="param-item">
            <span class="param-label">车辆数量</span>
            <a-slider
              v-model:value="compareB.params.vehicleCount"
              :min="1"
              :max="50"
              :step="1"
              style="width: 100px"
            />
            <span class="param-value">{{ compareB.params.vehicleCount }} 台</span>
          </div>
        </div>
      </div>
      <div class="compare-actions">
        <a-space>
          <a-input-number v-model:value="pendingTaskCount" :min="1" :max="200" />
          <a-button type="primary" :loading="comparing" @click="runCompare">对比仿真</a-button>
        </a-space>
      </div>
    </template>

    <div v-if="snapshot" class="metrics-row">
      <div class="metric-card"><span>待派任务</span><strong>{{ snapshot.pendingTaskCount }}</strong></div>
      <div class="metric-card"><span>OPEN 异常</span><strong>{{ snapshot.openExceptionCount }}</strong></div>
      <div class="metric-card"><span>空闲车辆</span><strong>{{ snapshot.idleVehicleCount }}</strong></div>
      <div class="metric-card warn"><span>低电量</span><strong>{{ snapshot.lowBatteryVehicleCount }}</strong></div>
    </div>

    <!-- P2-3 / P2-6: 数据时间戳与地图版本号显示 -->
    <div v-if="snapshot" class="snapshot-meta-bar">
      <div class="meta-item" :class="{ stale: isDataStale }">
        <span class="meta-label">数据最后更新:</span>
        <span class="meta-value">{{ formattedLastUpdated }}</span>
        <a-tag v-if="isDataStale" color="error" class="meta-tag">数据延迟</a-tag>
      </div>
      <div class="meta-item">
        <span class="meta-label">地图版本:</span>
        <a-tag color="blue" class="meta-tag">{{ mapVersionLabel }}</a-tag>
      </div>
      <!-- P2-1: 轨迹图例 -->
      <div class="meta-item legend">
        <span class="legend-item"><span class="legend-line plan"></span>规划</span>
        <span class="legend-item"><span class="legend-line actual"></span>实际</span>
        <span class="legend-item"><span class="legend-line predicted"></span>预测</span>
        <span class="legend-item"><span class="legend-line history"></span>历史</span>
      </div>
    </div>

    <a-alert
      type="info"
      show-icon
      message="态势为实时快照；仿真支持引擎 dry-run 与规则估算"
      description="仿真结果将标注「引擎仿真」或「估算仿真」。点击车辆可查看详情，拖拽画布可框选统计。"
      class="estimate-alert"
    />

    <a-spin :spinning="loading">
      <div ref="stageRef" class="twin-stage">
        <canvas
          ref="canvasRef"
          class="twin-canvas"
          @click="onCanvasClick"
          @mousedown="onMouseDown"
          @mousemove="onMouseMove"
          @mouseup="onMouseUp"
          @mouseleave="onMouseLeaveCanvas"
        />
        <div v-if="!loading && loadError" class="twin-empty">{{ loadError }}</div>
        <div v-else-if="!loading && !hasLayout" class="twin-empty">暂无园区地图数据，请检查默认园区配置</div>

        <!-- V9-UX4: Vehicle hover tooltip -->
        <div
          v-if="hoverTooltip.visible"
          class="vehicle-hover-tooltip"
          :style="{ left: hoverTooltip.x + 'px', top: (hoverTooltip.y - 28) + 'px' }"
        >
          {{ hoverTooltip.code }}
        </div>

        <!-- Area stats popover -->
        <div
          v-if="areaStats && areaStatsVisible"
          class="area-stats-popover"
          :style="areaStatsPos"
        >
          <div class="area-stats-content">
            区域内: <strong>{{ areaStats.vehicleCount }}</strong> 辆车,
            <strong>{{ areaStats.stationCount }}</strong> 个站点
          </div>
        </div>
      </div>
    </a-spin>

    <!-- Simulation result alert (single mode) -->
    <a-alert
      v-if="simulateResult && mode === 'SINGLE'"
      type="info"
      show-icon
      :message="simulateResult.summary"
      class="simulate-alert"
    >
      <template #description>
        <p>
          <a-tag :color="simulateResult.simulationMode === 'ENGINE' ? 'green' : 'orange'">
            {{ simulateResult.simulationMode === 'ENGINE' ? '引擎仿真' : '估算仿真' }}
          </a-tag>
          预计耗时 {{ simulateResult.estimatedMinutes }} 分钟 · 建议投入 {{ simulateResult.recommendedVehicleCount }} 台车
        </p>
        <p v-if="simulateResult.dispatchEfficiency != null">
          派车效率 {{ simulateResult.dispatchEfficiency }}% ·
          平均等待 {{ simulateResult.avgWaitTime }} min ·
          SOC 消耗 {{ simulateResult.socConsumption }}% ·
          完成订单 {{ simulateResult.completedOrders }}
        </p>
        <ul>
          <li v-for="(note, i) in simulateResult.notes" :key="i">{{ note }}</li>
        </ul>
      </template>
    </a-alert>

    <!-- Comparison results table -->
    <div v-if="comparisonResult && mode === 'COMPARE'" class="compare-result">
      <h4 class="compare-result-title">对比结果</h4>
      <a-table
        :data-source="comparisonRows"
        :columns="comparisonColumns"
        :pagination="false"
        size="small"
        bordered
      >
        <template #bodyCell="{ column, text, record }">
          <template v-if="column.key === 'metric'">
            <strong>{{ text }}</strong>
          </template>
          <template v-else-if="column.key === 'valueA'">
            <span :class="record.winner === 'A' ? 'winner-cell' : 'loser-cell'">
              {{ text }}
              <a-tag v-if="record.winner === 'A'" color="green" style="margin-left: 4px">优</a-tag>
            </span>
          </template>
          <template v-else-if="column.key === 'valueB'">
            <span :class="record.winner === 'B' ? 'winner-cell' : 'loser-cell'">
              {{ text }}
              <a-tag v-if="record.winner === 'B'" color="green" style="margin-left: 4px">优</a-tag>
            </span>
          </template>
        </template>
      </a-table>
      <a-alert
        type="success"
        show-icon
        :message="comparisonResult.overallRecommendation"
        class="compare-summary-alert"
      />
    </div>

    <!-- Vehicle detail drawer -->
    <a-drawer
      v-model:open="vehicleDrawerVisible"
      title="车辆详情"
      placement="right"
      width="400"
    >
      <template v-if="selectedVehicle">
        <div class="vehicle-detail">
          <div class="detail-row">
            <span class="label">车辆编码</span>
            <span class="value">{{ selectedVehicle.vehicleCode }}</span>
          </div>
          <div class="detail-row">
            <span class="label">车辆名称</span>
            <span class="value">{{ selectedVehicle.vehicleName }}</span>
          </div>
          <div class="detail-row">
            <span class="label">在线状态</span>
            <a-tag :color="selectedVehicle.onlineStatus === 'ONLINE' ? 'green' : 'default'">
              {{ selectedVehicle.onlineStatus === 'ONLINE' ? '在线' : '离线' }}
            </a-tag>
          </div>
          <div class="detail-row">
            <span class="label">调度状态</span>
            <a-tag>{{ dispatchStatusLabel }}</a-tag>
          </div>
          <div class="detail-row">
            <span class="label">电量</span>
            <div class="battery-bar">
              <a-progress
                :percent="Math.round(selectedVehicle.batteryLevel)"
                :stroke-color="batteryColor"
                :format="formatSelectedVehicleBattery"
              />
            </div>
          </div>
          <div class="detail-row">
            <span class="label">链接模式</span>
            <a-tag>{{ linkModeLabel }}</a-tag>
          </div>
          <div class="detail-row">
            <span class="label">当前任务</span>
            <span class="value">{{ selectedVehicle.currentTaskId ? `#${selectedVehicle.currentTaskId}` : '无' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">当前订单</span>
            <span class="value">{{ selectedVehicle.currentOrderId ? `#${selectedVehicle.currentOrderId}` : '无' }}</span>
          </div>

          <!-- P2-5: 车辆尺寸与通行约束（仅有值时显示） -->
          <div
            v-if="selectedVehicle.widthCm != null || selectedVehicle.lengthCm != null || selectedVehicle.turningRadiusM != null || selectedVehicle.allowedRoadClasses"
            class="detail-section"
          >车辆尺寸与通行约束</div>
          <div v-if="selectedVehicle.widthCm != null" class="detail-row">
            <span class="label">车辆宽度</span>
            <span class="value">{{ selectedVehicle.widthCm }} cm</span>
          </div>
          <div v-if="selectedVehicle.lengthCm != null" class="detail-row">
            <span class="label">车辆长度</span>
            <span class="value">{{ selectedVehicle.lengthCm }} cm</span>
          </div>
          <div v-if="selectedVehicle.turningRadiusM != null" class="detail-row">
            <span class="label">最小转弯半径</span>
            <span class="value">{{ selectedVehicle.turningRadiusM }} m</span>
          </div>
          <div v-if="selectedVehicle.allowedRoadClasses" class="detail-row">
            <span class="label">允许道路等级</span>
            <a-space wrap>
              <a-tag v-for="(cls, idx) in selectedVehicle.allowedRoadClasses.split(',')" :key="idx" color="blue">
                {{ cls.trim() }}
              </a-tag>
            </a-space>
          </div>

          <!-- P2-2: 运行阶段进度（到站/等待/装卸/充电/离站） -->
          <div class="detail-section">运行阶段进度</div>
          <div class="runtime-stage-row">
            <span class="label">当前阶段</span>
            <span class="value">{{ selectedVehicle.runtimeStage || '-' }}</span>
          </div>
          <div v-if="runtimeStageIndex >= 0" class="runtime-stage-steps">
            <div
              v-for="(step, idx) in RUNTIME_STAGE_STEPS"
              :key="step.code"
              class="stage-step"
              :class="{
                done: idx < runtimeStageIndex,
                active: idx === runtimeStageIndex,
              }"
            >
              <div class="stage-dot"></div>
              <div class="stage-label">{{ step.label }}</div>
            </div>
          </div>
          <div v-else class="trajectory-empty">未匹配到标准运行阶段</div>

          <!-- P2-1: 按类型分组的轨迹记录（PLAN/ACTUAL/PREDICTED/HISTORY） -->
          <div class="detail-section">轨迹记录</div>
          <div v-if="selectedVehicleTrajectories.length" class="trajectory-list">
            <div
              v-for="(group, gIdx) in selectedVehicleTrajectories"
              :key="gIdx"
              class="trajectory-group"
            >
              <div class="trajectory-group-title">
                <span
                  class="trajectory-type-badge"
                  :style="{ backgroundColor: TRAJECTORY_STYLE[group.type].color }"
                >{{ TRAJECTORY_TYPE_LABEL[group.type] }}</span>
                <span class="trajectory-count">{{ group.points.length }} 个点</span>
              </div>
              <div
                v-for="(pt, idx) in group.points.slice(-5).reverse()"
                :key="idx"
                class="trajectory-item"
              >
                {{ pt.code || '-' }} ({{ Number(pt.x).toFixed(1) }}, {{ Number(pt.y).toFixed(1) }})
              </div>
            </div>
          </div>
          <div v-else class="trajectory-empty">暂无轨迹数据</div>
        </div>
      </template>
    </a-drawer>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { compareSimulateDigitalTwin, getDigitalTwinSnapshot, simulateDigitalTwin } from '@/api/digitalTwin'
import { useParkScopeStore } from '@/stores/parkScope'
import type {
  DigitalTwinComparisonResult,
  DigitalTwinScenarioParams,
  DigitalTwinSimulateResult,
  DigitalTwinSnapshot,
  DigitalTwinAreaStats,
} from '@/types/phase10'
import type { ParkLayout, ParkPoint, ParkVehicleSnapshot, TrajectoryPointType } from '@/types/park'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const parkScope = useParkScopeStore()
const loading = ref(false)
const simulating = ref(false)
const comparing = ref(false)
const snapshot = ref<DigitalTwinSnapshot | null>(null)
const simulateResult = ref<DigitalTwinSimulateResult | null>(null)
const comparisonResult = ref<DigitalTwinComparisonResult | null>(null)
const loadError = ref('')
const scenario = ref('DISPATCH_PEAK')
const pendingTaskCount = ref(10)
const mode = ref<'SINGLE' | 'COMPARE'>('SINGLE')

const stageRef = ref<HTMLDivElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
let resizeObserver: ResizeObserver | null = null

// V5-DT1: Scenario parameters
const params = reactive<DigitalTwinScenarioParams>({
  speed: 1.0,
  orderInterval: 5,
  vehicleCount: 20,
})

// V5-DT2: Compare mode state
const compareA = reactive({
  scenario: 'DISPATCH_PEAK',
  params: { speed: 1.0, orderInterval: 5, vehicleCount: 20 } as DigitalTwinScenarioParams,
})
const compareB = reactive({
  scenario: 'TEXTILE_PEAK',
  params: { speed: 1.0, orderInterval: 5, vehicleCount: 20 } as DigitalTwinScenarioParams,
})

// V5-DT1: Vehicle click detection
const vehiclePositions = ref<Map<number, { x: number; y: number; vehicle: ParkVehicleSnapshot }>>(new Map())
const selectedVehicle = ref<ParkVehicleSnapshot | null>(null)
const vehicleDrawerVisible = ref(false)

// V9-UX4: Vehicle hover feedback
const hoveredVehicleId = ref<number | null>(null)
const hoverTooltip = ref<{ visible: boolean; x: number; y: number; code: string }>({
  visible: false,
  x: 0,
  y: 0,
  code: '',
})

// V5-DT1: Rectangle selection
const isDragging = ref(false)
const dragStart = ref<{ x: number; y: number } | null>(null)
const dragEnd = ref<{ x: number; y: number } | null>(null)
const areaStats = ref<DigitalTwinAreaStats | null>(null)
const areaStatsVisible = ref(false)
const areaStatsPos = ref<{ top: string; left: string }>({ top: '0px', left: '0px' })

const hasLayout = computed(() => Boolean(snapshot.value?.layout?.width && snapshot.value?.layout?.roadNodes?.length))

const scenarioOptions = [
  { label: '派车高峰', value: 'DISPATCH_PEAK' },
  { label: '家纺旺季', value: 'TEXTILE_PEAK' },
  { label: '充电高峰', value: 'CHARGING_SURGE' },
  { label: '异常风暴', value: 'EXCEPTION_STORM' },
]

const batteryColor = computed(() => {
  if (!selectedVehicle.value) return '#2DE08A'
  const lvl = selectedVehicle.value.batteryLevel
  if (lvl < 20) return '#FF5C7C'
  if (lvl < 50) return '#FFC04D'
  return '#2DE08A'
})

function formatSelectedVehicleBattery() {
  const level = selectedVehicle.value?.batteryLevel ?? 0
  return `${Math.round(level)}%`
}

const linkModeLabel = computed(() => {
  if (!selectedVehicle.value) return ''
  const map: Record<string, string> = { SIM: '仿真模式', REAL: '实车模式', VDA5050: 'VDA5050' }
  return map[selectedVehicle.value.linkMode] || selectedVehicle.value.linkMode
})

// P2-3: 数据时间戳显示与断线告警（>30s 视为延迟）
const nowTick = ref(Date.now())
let nowTickTimer: ReturnType<typeof setInterval> | null = null

const formattedLastUpdated = computed(() => {
  const ts = snapshot.value?.lastUpdatedAt
  if (!ts) return '未提供'
  return dayjs(ts).format('YYYY-MM-DD HH:mm:ss')
})

const isDataStale = computed(() => {
  const ts = snapshot.value?.lastUpdatedAt
  if (!ts) return false
  const diffSec = dayjs(nowTick.value).diff(dayjs(ts), 'second')
  return diffSec > 30
})

// P2-6: 地图版本号显示
const mapVersionLabel = computed(() => {
  return snapshot.value?.mapDataVersion || '未标注'
})

// P2-1: 轨迹类型样式映射（颜色 + 是否虚线）
const TRAJECTORY_STYLE: Record<TrajectoryPointType, { color: string; dash: number[] }> = {
  PLAN: { color: '#1890ff', dash: [6, 4] },
  ACTUAL: { color: '#52c41a', dash: [] },
  PREDICTED: { color: '#fa8c16', dash: [6, 4] },
  HISTORY: { color: '#8c8c8c', dash: [] },
}

const TRAJECTORY_TYPE_LABEL: Record<TrajectoryPointType, string> = {
  PLAN: '规划',
  ACTUAL: '实际',
  PREDICTED: '预测',
  HISTORY: '历史',
}

// P2-1: 将车辆的不同轨迹字段统一打上类型标签，便于按颜色渲染
function labeledTrajectory(points: ParkPoint[] | undefined, fallbackType: TrajectoryPointType): ParkPoint[] {
  if (!points || points.length === 0) return []
  return points.map((p) => ({ ...p, type: p.type ?? fallbackType }))
}

interface LabeledTrajectory {
  type: TrajectoryPointType
  points: ParkPoint[]
}

// 汇总当前选中车辆的所有轨迹（按类型分组），用于详情抽屉列表展示
const selectedVehicleTrajectories = computed<LabeledTrajectory[]>(() => {
  const v = selectedVehicle.value
  if (!v) return []
  const groups: LabeledTrajectory[] = []
  const plan = labeledTrajectory(v.plannedRouteGeo, 'PLAN')
  const actual = labeledTrajectory(v.trajectory, 'ACTUAL')
  const history = labeledTrajectory(v.geoTrajectory, 'HISTORY')
  if (plan.length) groups.push({ type: 'PLAN', points: plan })
  if (actual.length) groups.push({ type: 'ACTUAL', points: actual })
  if (history.length) groups.push({ type: 'HISTORY', points: history })
  return groups
})

// P2-2: 运行阶段进度（到站/等待/装卸/充电/离站）
interface RuntimeStageStep {
  code: string
  label: string
}

const RUNTIME_STAGE_STEPS: RuntimeStageStep[] = [
  { code: 'IDLE', label: '空闲' },
  { code: 'TO_PICKUP', label: '前往取货' },
  { code: 'AT_PICKUP', label: '取货中' },
  { code: 'TO_DROPOFF', label: '前往送货' },
  { code: 'AT_DROPOFF', label: '卸货中' },
  { code: 'TO_CHARGING', label: '前往充电' },
  { code: 'AT_CHARGING', label: '充电中' },
  { code: 'CHARGING_DONE', label: '离站' },
]

const runtimeStageIndex = computed(() => {
  const stage = selectedVehicle.value?.runtimeStage
  if (!stage) return -1
  const idx = RUNTIME_STAGE_STEPS.findIndex((s) => s.code === stage)
  return idx
})

const dispatchStatusLabel = computed(() => {
  const status = selectedVehicle.value?.dispatchStatus
  if (!status) return '-'
  const map: Record<string, string> = {
    IDLE: '空闲',
    BUSY: '忙碌',
    UNAVAILABLE: '不可用',
  }
  return map[status] || status
})

// Comparison table
interface ComparisonRow {
  key: string
  metric: string
  valueA: string
  valueB: string
  winner: 'A' | 'B' | 'TIE'
}

const comparisonColumns = [
  { title: '指标', dataIndex: 'metric', key: 'metric', width: 160 },
  { title: '场景 A', dataIndex: 'valueA', key: 'valueA', align: 'center' as const },
  { title: '场景 B', dataIndex: 'valueB', key: 'valueB', align: 'center' as const },
]

const comparisonRows = computed<ComparisonRow[]>(() => {
  if (!comparisonResult.value) return []
  const ra = comparisonResult.value.scenarioA.result
  const rb = comparisonResult.value.scenarioB.result
  if (!ra || !rb) return []

  const rows: ComparisonRow[] = [
    {
      key: 'dispatchEfficiency',
      metric: '派车效率',
      valueA: `${ra.dispatchEfficiency ?? '-'}%`,
      valueB: `${rb.dispatchEfficiency ?? '-'}%`,
      winner: pickWinner(ra.dispatchEfficiency, rb.dispatchEfficiency, true),
    },
    {
      key: 'avgWaitTime',
      metric: '平均等待时间',
      valueA: `${ra.avgWaitTime ?? '-'} min`,
      valueB: `${rb.avgWaitTime ?? '-'} min`,
      winner: pickWinner(ra.avgWaitTime, rb.avgWaitTime, false),
    },
    {
      key: 'socConsumption',
      metric: 'SOC 消耗',
      valueA: `${ra.socConsumption ?? '-'}%`,
      valueB: `${rb.socConsumption ?? '-'}%`,
      winner: pickWinner(ra.socConsumption, rb.socConsumption, false),
    },
    {
      key: 'completedOrders',
      metric: '完成订单数',
      valueA: `${ra.completedOrders ?? '-'}`,
      valueB: `${rb.completedOrders ?? '-'}`,
      winner: pickWinner(ra.completedOrders, rb.completedOrders, true),
    },
    {
      key: 'estimatedMinutes',
      metric: '预估耗时',
      valueA: `${ra.estimatedMinutes ?? '-'} min`,
      valueB: `${rb.estimatedMinutes ?? '-'} min`,
      winner: pickWinner(ra.estimatedMinutes, rb.estimatedMinutes, false),
    },
    {
      key: 'recommendedVehicleCount',
      metric: '建议车辆数',
      valueA: `${ra.recommendedVehicleCount ?? '-'} 台`,
      valueB: `${rb.recommendedVehicleCount ?? '-'} 台`,
      winner: 'TIE' as const,
    },
  ]
  return rows
})

function pickWinner(a: number | undefined | null, b: number | undefined | null, higherBetter: boolean): 'A' | 'B' | 'TIE' {
  if (a == null && b == null) return 'TIE'
  if (a == null) return 'B'
  if (b == null) return 'A'
  if (a === b) return 'TIE'
  if (higherBetter) return a > b ? 'A' : 'B'
  return a < b ? 'A' : 'B'
}

async function scheduleDraw(layout: ParkLayout | null, vehicles: ParkVehicleSnapshot[]) {
  await nextTick()
  requestAnimationFrame(() => drawTwin(layout, vehicles))
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    parkScope.ensureValidSelection()
    const res = await getDigitalTwinSnapshot(parkScope.selectedParkId)
    snapshot.value = res.data
    await scheduleDraw(res.data.layout, res.data.vehicles || [])
  } catch (err) {
    snapshot.value = null
    if (err instanceof Error && err.message === 'PARK_NOT_FOUND') {
      parkScope.ensureValidSelection()
      try {
        const retry = await getDigitalTwinSnapshot(undefined)
        snapshot.value = retry.data
        await scheduleDraw(retry.data.layout, retry.data.vehicles || [])
        return
      } catch {
        loadError.value = '园区数据加载失败，请刷新重试'
      }
    } else {
      loadError.value = '孪生态势加载失败，请确认后端已启动'
    }
  } finally {
    loading.value = false
  }
}

async function runSimulate() {
  simulating.value = true
  try {
    const res = await simulateDigitalTwin({
      parkId: parkScope.selectedParkId,
      scenario: scenario.value,
      pendingTaskCount: pendingTaskCount.value,
      speed: params.speed,
      orderInterval: params.orderInterval,
      vehicleCount: params.vehicleCount,
    })
    simulateResult.value = res.data
  } finally {
    simulating.value = false
  }
}

async function runCompare() {
  comparing.value = true
  comparisonResult.value = null
  try {
    const res = await compareSimulateDigitalTwin({
      parkId: parkScope.selectedParkId,
      scenarioA: compareA.scenario,
      paramsA: { ...compareA.params },
      scenarioB: compareB.scenario,
      paramsB: { ...compareB.params },
      pendingTaskCount: pendingTaskCount.value,
    })
    comparisonResult.value = res.data
  } finally {
    comparing.value = false
  }
}

function drawTwin(layout: ParkLayout | null, vehicles: ParkVehicleSnapshot[]) {
  const stage = stageRef.value
  const canvas = canvasRef.value
  if (!stage || !canvas || !layout?.width || !layout?.height) return

  const dpr = window.devicePixelRatio || 1
  const width = stage.clientWidth
  const height = stage.clientHeight
  if (width <= 0 || height <= 0) return

  canvas.width = width * dpr
  canvas.height = height * dpr
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`

  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, width, height)

  const gradient = ctx.createLinearGradient(0, 0, 0, height)
  gradient.addColorStop(0, '#0B1018')
  gradient.addColorStop(1, '#1A2230')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, width, height)

  const pad = 48
  const scale = Math.min((width - pad * 2) / layout.width, (height - pad * 2) / layout.height) * 0.9
  const offsetX = width / 2
  const offsetY = height * 0.62
  const isoX = (x: number, y: number) => offsetX + (x - y) * scale * 0.5
  const isoY = (x: number, y: number) => offsetY + (x + y) * scale * 0.22

  const nodeMap = new Map((layout.roadNodes || []).map((n) => [n.code, n]))
  ctx.strokeStyle = 'rgba(100, 180, 255, 0.35)'
  ctx.lineWidth = 2
  for (const seg of layout.roadSegments || []) {
    const from = nodeMap.get(seg.from)
    const to = nodeMap.get(seg.to)
    if (!from || !to) continue
    ctx.beginPath()
    ctx.moveTo(isoX(from.x, from.y), isoY(from.x, from.y))
    ctx.lineTo(isoX(to.x, to.y), isoY(to.x, to.y))
    ctx.stroke()
  }

  for (const station of layout.stations || []) {
    const x = isoX(station.x, station.y)
    const y = isoY(station.x, station.y)
    ctx.fillStyle = 'rgba(34, 199, 230, 0.35)'
    ctx.fillRect(x - 10, y - 6, 20, 12)
    ctx.fillStyle = '#22C7E6'
    ctx.fillRect(x - 8, y - 14, 16, 8)
  }

  // P2-1: 绘制车辆轨迹（按类型使用不同颜色与虚实线）
  // 顺序：HISTORY -> PLAN -> ACTUAL -> PREDICTED（让实际轨迹覆盖在最上层）
  const trajectoryDrawOrder: TrajectoryPointType[] = ['HISTORY', 'PLAN', 'ACTUAL', 'PREDICTED']
  for (const vehicle of vehicles) {
    const typedGroups: { type: TrajectoryPointType; points: ParkPoint[] }[] = [
      { type: 'HISTORY', points: labeledTrajectory(vehicle.geoTrajectory, 'HISTORY') },
      { type: 'PLAN', points: labeledTrajectory(vehicle.plannedRouteGeo, 'PLAN') },
      { type: 'ACTUAL', points: labeledTrajectory(vehicle.trajectory, 'ACTUAL') },
    ]
    for (const drawType of trajectoryDrawOrder) {
      const group = typedGroups.find((g) => g.type === drawType)
      if (!group || group.points.length < 2) continue
      const style = TRAJECTORY_STYLE[drawType]
      ctx.strokeStyle = style.color
      ctx.lineWidth = 2
      ctx.setLineDash(style.dash)
      ctx.beginPath()
      let started = false
      for (const pt of group.points) {
        if (pt.x == null || pt.y == null) continue
        const px = isoX(Number(pt.x), Number(pt.y))
        const py = isoY(Number(pt.x), Number(pt.y))
        if (!started) {
          ctx.moveTo(px, py)
          started = true
        } else {
          ctx.lineTo(px, py)
        }
      }
      ctx.stroke()
      ctx.setLineDash([])
    }
  }

  vehiclePositions.value = new Map()
  for (const vehicle of vehicles) {
    if (vehicle.x == null || vehicle.y == null) continue
    const x = isoX(Number(vehicle.x), Number(vehicle.y))
    const y = isoY(Number(vehicle.x), Number(vehicle.y))
    const color = vehicle.lowBattery ? '#FF5C7C' : vehicle.charging ? '#FFC04D' : '#2DE08A'

    // V9-UX4: Hover highlight — translucent ring around hovered vehicle
    if (hoveredVehicleId.value === vehicle.vehicleId) {
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.6)'
      ctx.lineWidth = 1.5
      ctx.beginPath()
      ctx.arc(x, y - 10, 11, 0, Math.PI * 2)
      ctx.stroke()
    }

    // Highlight selected vehicle
    if (selectedVehicle.value?.vehicleId === vehicle.vehicleId) {
      ctx.strokeStyle = '#ffffff'
      ctx.lineWidth = 2
      ctx.beginPath()
      ctx.arc(x, y - 10, 10, 0, Math.PI * 2)
      ctx.stroke()
    }

    ctx.fillStyle = color
    ctx.beginPath()
    ctx.arc(x, y - 10, 7, 0, Math.PI * 2)
    ctx.fill()
    ctx.fillStyle = 'rgba(255,255,255,0.9)'
    ctx.font = '11px sans-serif'
    ctx.fillText(vehicle.vehicleCode, x - 24, y - 22)

    vehiclePositions.value.set(vehicle.vehicleId, { x, y, vehicle })
  }

  // Draw selection rectangle
  if (isDragging.value && dragStart.value && dragEnd.value) {
    const sx = Math.min(dragStart.value.x, dragEnd.value.x)
    const sy = Math.min(dragStart.value.y, dragEnd.value.y)
    const ex = Math.max(dragStart.value.x, dragEnd.value.x)
    const ey = Math.max(dragStart.value.y, dragEnd.value.y)
    ctx.strokeStyle = 'rgba(34, 199, 230, 0.8)'
    ctx.lineWidth = 1.5
    ctx.setLineDash([4, 4])
    ctx.strokeRect(sx, sy, ex - sx, ey - sy)
    ctx.fillStyle = 'rgba(34, 199, 230, 0.08)'
    ctx.fillRect(sx, sy, ex - sx, ey - sy)
    ctx.setLineDash([])
  }
}

// V5-DT1: Canvas click handler for vehicle detection
// V9-UX5: Reduced hit-test threshold from 15px to 10px to avoid misclicks in dense areas
function findVehicleAt(x: number, y: number, threshold = 10): number | null {
  let closestId: number | null = null
  let closestDist = Infinity
  for (const [id, pos] of vehiclePositions.value.entries()) {
    const dist = Math.hypot(x - pos.x, y - (pos.y - 10))
    if (dist < threshold && dist < closestDist) {
      closestDist = dist
      closestId = id
    }
  }
  return closestId
}

function onCanvasClick(e: MouseEvent) {
  const canvas = canvasRef.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  const clickX = e.clientX - rect.left
  const clickY = e.clientY - rect.top

  // Hit-test: find closest vehicle within 10px (V9-UX5: lowered from 15px)
  const closestId = findVehicleAt(clickX, clickY, 10)

  if (closestId != null) {
    const entry = vehiclePositions.value.get(closestId)
    if (entry) {
      selectedVehicle.value = entry.vehicle
      vehicleDrawerVisible.value = true
      // Re-draw to show highlight
      scheduleDraw(snapshot.value?.layout ?? null, snapshot.value?.vehicles ?? [])
    }
  }
}

// V5-DT1: Rectangle selection handlers
function onMouseDown(e: MouseEvent) {
  const canvas = canvasRef.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  isDragging.value = true
  dragStart.value = { x: e.clientX - rect.left, y: e.clientY - rect.top }
  dragEnd.value = { x: e.clientX - rect.left, y: e.clientY - rect.top }
  areaStatsVisible.value = false
}

function onMouseMove(e: MouseEvent) {
  const canvas = canvasRef.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  const mx = e.clientX - rect.left
  const my = e.clientY - rect.top

  if (isDragging.value) {
    dragEnd.value = { x: mx, y: my }
    // Re-draw to show rectangle
    drawTwin(snapshot.value?.layout ?? null, snapshot.value?.vehicles ?? [])
    return
  }

  // V9-UX4: Hover detection — update cursor and tooltip when over a vehicle
  const hitId = findVehicleAt(mx, my, 10)
  if (hitId !== hoveredVehicleId.value) {
    const prevId = hoveredVehicleId.value
    hoveredVehicleId.value = hitId
    // Re-draw only when hover state changes to show/hide highlight ring
    if (hitId !== null || prevId !== null) {
      drawTwin(snapshot.value?.layout ?? null, snapshot.value?.vehicles ?? [])
    }
  }

  if (hitId != null) {
    const entry = vehiclePositions.value.get(hitId)
    canvas.style.cursor = 'pointer'
    hoverTooltip.value = {
      visible: true,
      x: mx,
      y: my,
      code: entry?.vehicle.vehicleCode ?? '',
    }
  } else {
    canvas.style.cursor = ''
    hoverTooltip.value = { ...hoverTooltip.value, visible: false }
  }
}

function onMouseLeaveCanvas() {
  // Cancel any in-progress drag without triggering selection
  if (isDragging.value) {
    isDragging.value = false
    dragStart.value = null
    dragEnd.value = null
    drawTwin(snapshot.value?.layout ?? null, snapshot.value?.vehicles ?? [])
  }
  // V9-UX4: Clear hover state
  if (hoveredVehicleId.value !== null) {
    hoveredVehicleId.value = null
    drawTwin(snapshot.value?.layout ?? null, snapshot.value?.vehicles ?? [])
  }
  const canvas = canvasRef.value
  if (canvas) canvas.style.cursor = ''
  hoverTooltip.value = { ...hoverTooltip.value, visible: false }
}

function onMouseUp(_e: MouseEvent) {
  if (!isDragging.value || !dragStart.value || !dragEnd.value) {
    isDragging.value = false
    return
  }

  const canvas = canvasRef.value
  if (!canvas) return

  // Compute bounds in canvas space
  const minX = Math.min(dragStart.value.x, dragEnd.value.x)
  const maxX = Math.max(dragStart.value.x, dragEnd.value.x)
  const minY = Math.min(dragStart.value.y, dragEnd.value.y)
  const maxY = Math.max(dragStart.value.y, dragEnd.value.y)

  // Only count if selection area > 20px² to avoid accidental clicks
  if (maxX - minX > 20 && maxY - minY > 20) {
    let vCount = 0
    for (const [, pos] of vehiclePositions.value.entries()) {
      if (pos.x >= minX && pos.x <= maxX && (pos.y - 10) >= minY && (pos.y - 10) <= maxY) {
        vCount++
      }
    }

    // Count stations in the area
    const layout = snapshot.value?.layout
    let sCount = 0
    if (layout) {
      const pad = 48
      const scale = Math.min((canvas.clientWidth - pad * 2) / layout.width, (canvas.clientHeight - pad * 2) / layout.height) * 0.9
      const offsetX = canvas.clientWidth / 2
      const offsetY = canvas.clientHeight * 0.62
      const isoXfn = (x: number, y: number) => offsetX + (x - y) * scale * 0.5
      const isoYfn = (x: number, y: number) => offsetY + (x + y) * scale * 0.22

      for (const st of layout.stations || []) {
        const sx = isoXfn(st.x, st.y)
        const sy = isoYfn(st.x, st.y)
        if (sx >= minX && sx <= maxX && sy >= minY && sy <= maxY) {
          sCount++
        }
      }
    }

    areaStats.value = {
      vehicleCount: vCount,
      stationCount: sCount,
      bounds: { minX, minY, maxX, maxY },
    }

    // Position popover near selection center
    const rect = canvas.getBoundingClientRect()
    areaStatsPos.value = {
      top: `${maxY + rect.top + 8}px`,
      left: `${(minX + maxX) / 2 + rect.left - 100}px`,
    }
    areaStatsVisible.value = true
  }

  isDragging.value = false
  dragStart.value = null
  dragEnd.value = null
  // Re-draw to remove rectangle
  drawTwin(snapshot.value?.layout ?? null, snapshot.value?.vehicles ?? [])
}

watch(
  () => parkScope.selectedParkId,
  () => load()
)

onMounted(async () => {
  if (parkScope.parks.length === 0) {
    await parkScope.loadParks()
  }
  parkScope.ensureValidSelection()
  await load()
  if (stageRef.value) {
    resizeObserver = new ResizeObserver(() => {
      if (snapshot.value) {
        drawTwin(snapshot.value.layout, snapshot.value.vehicles || [])
      }
    })
    resizeObserver.observe(stageRef.value)
  }
  // P2-3: 每秒刷新 nowTick，用于驱动 isDataStale 计算与告警样式
  nowTickTimer = setInterval(() => {
    nowTick.value = Date.now()
  }, 1000)
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  if (nowTickTimer) {
    clearInterval(nowTickTimer)
    nowTickTimer = null
  }
})
</script>

<style scoped lang="less">
.metrics-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.estimate-alert {
  margin-bottom: 16px;
}

.metric-card {
  background: var(--fsd-bg-elevated);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--fsd-text-secondary);

  strong {
    color: var(--fsd-text-primary);
    font-size: 20px;
  }
}

.metric-card.warn strong {
  color: var(--fsd-error);
}

.twin-stage {
  position: relative;
  height: min(68vh, 640px);
  border-radius: var(--fsd-radius-lg);
  overflow: hidden;
  border: 1px solid var(--fsd-border);
  background: var(--fsd-bg-deep);
}

.twin-canvas {
  width: 100%;
  height: 100%;
  display: block;
  cursor: crosshair;
}

/* V9-UX4: Vehicle hover tooltip */
.vehicle-hover-tooltip {
  position: absolute;
  z-index: 10;
  pointer-events: none;
  transform: translateX(-50%);
  background: var(--fsd-bg-elevated, #1A2230);
  border: 1px solid var(--fsd-border, rgba(255, 255, 255, 0.07));
  border-radius: var(--fsd-radius, 6px);
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--fsd-text-primary, #EEF3F9);
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.35);
}

.twin-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--fsd-text-tertiary);
  font-size: 14px;
}

.simulate-alert ul {
  margin: 8px 0 0;
  padding-left: 18px;
}

/* V5-DT1: Scenario parameter controls */
.scenario-controls {
  margin-bottom: 8px;
}

.params-row {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 12px;
  padding: 8px 16px;
  background: var(--fsd-bg-elevated);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
}

.param-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.param-label {
  font-size: 13px;
  color: var(--fsd-text-secondary);
  white-space: nowrap;
  min-width: 56px;
}

.param-value {
  font-size: 13px;
  color: var(--fsd-text-primary);
  font-weight: 600;
  min-width: 48px;
}

/* V5-DT2: Compare mode panels */
.compare-panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 12px;
}

.compare-panel {
  background: var(--fsd-bg-elevated);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
  padding: 12px 16px;
}

.panel-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.panel-a .panel-title {
  color: var(--fsd-accent);
}

.panel-b .panel-title {
  color: var(--fsd-warning);
}

.compare-panel .param-item {
  margin-top: 6px;
}

.compare-actions {
  margin-bottom: 12px;
}

.compare-result {
  margin-top: 16px;
}

.compare-result-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
}

.winner-cell {
  color: var(--fsd-success);
  font-weight: 600;
}

.loser-cell {
  color: var(--fsd-text-secondary);
}

.compare-summary-alert {
  margin-top: 12px;
}

/* V5-DT1: Area stats popover */
.area-stats-popover {
  position: fixed;
  z-index: 100;
  pointer-events: none;
}

.area-stats-content {
  background: var(--fsd-bg-elevated);
  border: 1px solid var(--fsd-accent);
  border-radius: var(--fsd-radius);
  padding: 8px 14px;
  font-size: 13px;
  color: var(--fsd-text-primary);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  white-space: nowrap;
}

/* V5-DT1: Vehicle detail drawer */
.vehicle-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 8px;

  .label {
    font-size: 13px;
    color: var(--fsd-text-secondary);
    min-width: 72px;
    flex-shrink: 0;
  }

  .value {
    font-size: 14px;
    color: var(--fsd-text-primary);
  }

  .battery-bar {
    flex: 1;
  }
}

.detail-section {
  font-size: 14px;
  font-weight: 600;
  color: var(--fsd-text-primary);
  border-top: 1px solid var(--fsd-border);
  padding-top: 8px;
  margin-top: 4px;
}

.trajectory-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.trajectory-item {
  font-size: 12px;
  color: var(--fsd-text-secondary);
  font-family: monospace;
  padding: 2px 0;
}

.trajectory-empty {
  font-size: 13px;
  color: var(--fsd-text-tertiary);
}

/* Mobile responsive */
@media (max-width: 768px) {
  .metrics-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .params-row {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .compare-panels {
    grid-template-columns: 1fr;
  }
}

/* P2-3 / P2-6: 顶部数据时间戳与地图版本号 */
.snapshot-meta-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 24px;
  padding: 8px 16px;
  margin-bottom: 4px;
  background: var(--fsd-bg-elevated);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
  font-size: 13px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--fsd-text-secondary);

  &.stale {
    color: var(--fsd-error);

    .meta-value {
      color: var(--fsd-error);
      font-weight: 600;
    }
  }

  &.legend {
    gap: 16px;
    margin-left: auto;
  }
}

.meta-label {
  color: var(--fsd-text-tertiary);
}

.meta-value {
  font-family: 'JetBrains Mono', monospace;
  color: var(--fsd-text-primary);
}

.meta-tag {
  margin-left: 4px;
}

/* P2-1: 轨迹图例 */
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--fsd-text-secondary);
  font-size: 12px;
}

.legend-line {
  display: inline-block;
  width: 18px;
  height: 0;
  border-top-width: 2px;
  border-top-style: solid;

  &.plan {
    border-top-color: #1890ff;
    border-top-style: dashed;
  }

  &.actual {
    border-top-color: #52c41a;
  }

  &.predicted {
    border-top-color: #fa8c16;
    border-top-style: dashed;
  }

  &.history {
    border-top-color: #8c8c8c;
  }
}

/* P2-2: 运行阶段进度 */
.runtime-stage-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;

  .label {
    font-size: 13px;
    color: var(--fsd-text-secondary);
    min-width: 72px;
    flex-shrink: 0;
  }

  .value {
    font-size: 14px;
    color: var(--fsd-text-primary);
    font-family: 'JetBrains Mono', monospace;
  }
}

.runtime-stage-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 4px;
  padding: 8px 0;
}

.stage-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex: 1 1 80px;
  min-width: 64px;

  .stage-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--fsd-border);
    border: 2px solid transparent;
  }

  .stage-label {
    font-size: 11px;
    color: var(--fsd-text-tertiary);
    text-align: center;
  }

  &.done .stage-dot {
    background: var(--fsd-success);
  }

  &.active .stage-dot {
    background: var(--fsd-accent);
    box-shadow: 0 0 0 3px rgba(34, 199, 230, 0.25);
  }

  &.active .stage-label {
    color: var(--fsd-text-primary);
    font-weight: 600;
  }
}

/* P2-1: 按类型分组的轨迹列表 */
.trajectory-group {
  margin-bottom: 8px;
  padding: 6px 8px;
  border-radius: var(--fsd-radius);
  background: var(--fsd-bg-deep);
}

.trajectory-group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.trajectory-type-badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 10px;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
}

.trajectory-count {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
}
</style>