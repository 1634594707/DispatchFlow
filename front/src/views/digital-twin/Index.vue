<template>
  <PageContainer title="数字孪生" subtitle="3D 风格园区态势 · 实时车辆叠加 · 调度方案预评估">
    <template #actions>
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
    </template>

    <div v-if="snapshot" class="metrics-row">
      <div class="metric-card"><span>待派任务</span><strong>{{ snapshot.pendingTaskCount }}</strong></div>
      <div class="metric-card"><span>OPEN 异常</span><strong>{{ snapshot.openExceptionCount }}</strong></div>
      <div class="metric-card"><span>空闲车辆</span><strong>{{ snapshot.idleVehicleCount }}</strong></div>
      <div class="metric-card warn"><span>低电量</span><strong>{{ snapshot.lowBatteryVehicleCount }}</strong></div>
    </div>

    <a-alert
      type="warning"
      show-icon
      message="当前为估算态势，非引擎级回放"
      description="车辆位置与仿真推演基于实时快照与规则估算；引擎级仿真与历史回放见 Phase 12。"
      class="estimate-alert"
    />

    <a-spin :spinning="loading">
      <div class="twin-stage" ref="stageRef">
        <canvas ref="canvasRef" class="twin-canvas" />
        <div v-if="!loading && loadError" class="twin-empty">{{ loadError }}</div>
        <div v-else-if="!loading && !hasLayout" class="twin-empty">暂无园区地图数据，请检查默认园区配置</div>
      </div>
    </a-spin>

    <a-alert
      v-if="simulateResult"
      type="info"
      show-icon
      :message="simulateResult.summary"
      class="simulate-alert"
    >
      <template #description>
        <p>预计耗时 {{ simulateResult.estimatedMinutes }} 分钟 · 建议投入 {{ simulateResult.recommendedVehicleCount }} 台车</p>
        <ul>
          <li v-for="(note, i) in simulateResult.notes" :key="i">{{ note }}</li>
        </ul>
      </template>
    </a-alert>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { getDigitalTwinSnapshot, simulateDigitalTwin } from '@/api/digitalTwin'
import { useParkScopeStore } from '@/stores/parkScope'
import type { DigitalTwinSimulateResult, DigitalTwinSnapshot } from '@/types/phase10'
import type { ParkLayout, ParkVehicleSnapshot } from '@/types/park'

const parkScope = useParkScopeStore()
const loading = ref(false)
const simulating = ref(false)
const snapshot = ref<DigitalTwinSnapshot | null>(null)
const simulateResult = ref<DigitalTwinSimulateResult | null>(null)
const loadError = ref('')
const scenario = ref('DISPATCH_PEAK')
const pendingTaskCount = ref(10)

const stageRef = ref<HTMLDivElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
let resizeObserver: ResizeObserver | null = null

const hasLayout = computed(() => Boolean(snapshot.value?.layout?.width && snapshot.value?.layout?.roadNodes?.length))

const scenarioOptions = [
  { label: '派车高峰', value: 'DISPATCH_PEAK' },
  { label: '充电高峰', value: 'CHARGING_SURGE' },
  { label: '异常风暴', value: 'EXCEPTION_STORM' },
]

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
    })
    simulateResult.value = res.data
  } finally {
    simulating.value = false
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
  gradient.addColorStop(0, '#0b1320')
  gradient.addColorStop(1, '#152238')
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
    ctx.fillStyle = 'rgba(0, 180, 216, 0.35)'
    ctx.fillRect(x - 10, y - 6, 20, 12)
    ctx.fillStyle = '#7dd3fc'
    ctx.fillRect(x - 8, y - 14, 16, 8)
  }

  for (const vehicle of vehicles) {
    if (vehicle.x == null || vehicle.y == null) continue
    const x = isoX(Number(vehicle.x), Number(vehicle.y))
    const y = isoY(Number(vehicle.x), Number(vehicle.y))
    const color = vehicle.lowBattery ? '#ff6b6b' : vehicle.charging ? '#ffd166' : '#4ade80'
    ctx.fillStyle = color
    ctx.beginPath()
    ctx.arc(x, y - 10, 7, 0, Math.PI * 2)
    ctx.fill()
    ctx.fillStyle = 'rgba(255,255,255,0.9)'
    ctx.font = '11px sans-serif'
    ctx.fillText(vehicle.vehicleCode, x - 24, y - 22)
  }
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
})

onUnmounted(() => resizeObserver?.disconnect())
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
  background: #0b1320;
}

.twin-canvas {
  width: 100%;
  height: 100%;
  display: block;
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
</style>
