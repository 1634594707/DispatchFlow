<template>
  <div class="mobile-order-page">
    <div class="mobile-toolbar">
      <router-link class="toolbar-link" to="/vehicle-tracking">返回大屏</router-link>
      <div class="toolbar-title">
        <span class="toolbar-eyebrow">DispatchFlow Mobile</span>
        <strong>园区下单</strong>
      </div>
      <button class="toolbar-submit" :disabled="submitting" @click="submitOrder">
        {{ submitting ? '提交中...' : '快速提交' }}
      </button>
    </div>

    <div class="mobile-shell">
      <header class="hero-card">
        <div class="hero-copy">
          <div class="hero-eyebrow">Smart Dispatch</div>
          <h1>像看外卖一样看配送</h1>
          <p>下单后直接在手机端查看车辆位置、订单阶段和当前目标点，不需要跳到管理大屏。</p>
        </div>
        <div class="hero-pills">
          <span class="hero-pill blue">{{ stations.length }} 个站点</span>
          <span class="hero-pill gold">{{ activeOrders.length }} 个进行中订单</span>
          <span class="hero-pill green">{{ vehicles.length }} 台车辆</span>
        </div>
      </header>

      <section v-if="trackedOrder" class="tracking-card">
        <div class="section-head">
          <span>实时配送图</span>
          <span class="section-tip">{{ stageLabel(trackedOrder.runtimeStage) }}</span>
        </div>

        <div v-if="activeOrders.length > 0" class="tracking-switch">
          <button
            v-for="order in activeOrders.slice(0, 5)"
            :key="order.orderId"
            type="button"
            class="tracking-chip"
            :class="{ active: trackedOrder.orderId === order.orderId }"
            @click="trackedOrderId = order.orderId"
          >
            {{ order.orderNo }}
          </button>
        </div>

        <div class="tracking-map-shell">
          <div v-if="parkLayout" class="tracking-map-wrap">
            <img src="/park-map.svg" alt="park map" class="tracking-map-image" />
            <svg
              class="tracking-map-overlay"
              :viewBox="`0 0 ${parkLayout.width} ${parkLayout.height}`"
              preserveAspectRatio="none"
            >
              <defs>
                <linearGradient id="routeGradient" x1="0%" x2="100%" y1="0%" y2="0%">
                  <stop offset="0%" stop-color="#3ea6ff" />
                  <stop offset="100%" stop-color="#74c2ff" />
                </linearGradient>
                <filter id="softShadow" x="-20%" y="-20%" width="140%" height="140%">
                  <feDropShadow dx="0" dy="6" stdDeviation="8" flood-color="#3ea6ff" flood-opacity="0.22" />
                </filter>
              </defs>

              <polyline
                :points="routePolylinePoints"
                class="map-main-route"
                filter="url(#softShadow)"
              />

              <line
                v-if="trackedVehicle && currentTarget"
                :x1="trackedVehicle.x"
                :y1="svgY(trackedVehicle.y)"
                :x2="currentTarget.x"
                :y2="svgY(currentTarget.y)"
                class="map-vehicle-route"
              />
            </svg>

            <div class="map-pin pickup" :style="markerStyle(trackedOrder.pickupStation.x, trackedOrder.pickupStation.y)">
              <span>{{ trackedOrder.pickupStation.stationCode }}</span>
            </div>
            <div class="map-pin dropoff" :style="markerStyle(trackedOrder.dropoffStation.x, trackedOrder.dropoffStation.y)">
              <span>{{ trackedOrder.dropoffStation.stationCode }}</span>
            </div>
            <div v-if="trackedVehicle" class="map-vehicle-badge" :style="markerStyle(trackedVehicle.x, trackedVehicle.y)">
              <span class="vehicle-ring"></span>
              <span class="vehicle-dot"></span>
              <span class="vehicle-code">{{ trackedVehicle.vehicleCode }}</span>
            </div>
          </div>

          <div class="tracking-legend">
            <span><i class="legend-dot pickup"></i>取货点</span>
            <span><i class="legend-dot dropoff"></i>送货点</span>
            <span><i class="legend-dot vehicle"></i>车辆</span>
          </div>
        </div>

        <div class="tracking-summary">
          <div class="summary-item">
            <label>订单号</label>
            <strong>{{ trackedOrder.orderNo }}</strong>
          </div>
          <div class="summary-item">
            <label>当前阶段</label>
            <strong>{{ stageLabel(trackedOrder.runtimeStage) }}</strong>
          </div>
          <div class="summary-item">
            <label>配送车辆</label>
            <strong>{{ trackedOrder.vehicleCode || '待分配' }}</strong>
          </div>
          <div class="summary-item">
            <label>当前目标</label>
            <strong>{{ currentTargetLabel }}</strong>
          </div>
        </div>

        <div class="tracking-timeline">
          <div class="timeline-step" :class="{ active: timelineState >= 1 }">
            <span class="step-dot"></span>
            <span>已接单</span>
          </div>
          <div class="timeline-step" :class="{ active: timelineState >= 2 }">
            <span class="step-dot"></span>
            <span>前往取货</span>
          </div>
          <div class="timeline-step" :class="{ active: timelineState >= 3 }">
            <span class="step-dot"></span>
            <span>配送中</span>
          </div>
          <div class="timeline-step" :class="{ active: timelineState >= 4 }">
            <span class="step-dot"></span>
            <span>已送达</span>
          </div>
        </div>
      </section>

      <section class="order-card">
        <div class="section-head">
          <span>创建订单</span>
          <span class="section-tip">选择站点后直接下发到调度系统</span>
        </div>

        <a-form layout="vertical">
          <a-form-item label="取货站点">
            <a-select
              v-model:value="form.pickupStationId"
              placeholder="选择取货站点"
              size="large"
              :loading="loadingStations"
              show-search
              option-filter-prop="label"
              :options="stationOptions"
            />
          </a-form-item>

          <a-form-item label="送货站点">
            <a-select
              v-model:value="form.dropoffStationId"
              placeholder="选择送货站点"
              size="large"
              :loading="loadingStations"
              show-search
              option-filter-prop="label"
              :options="dropoffOptions"
            />
          </a-form-item>

          <a-form-item label="优先级">
            <div class="priority-grid">
              <button
                v-for="priority in priorities"
                :key="priority.value"
                type="button"
                class="priority-card"
                :class="{ active: form.priority === priority.value }"
                @click="form.priority = priority.value"
              >
                <span class="priority-value">{{ priority.value }}</span>
                <span class="priority-label">{{ priority.label }}</span>
              </button>
            </div>
          </a-form-item>

          <a-form-item label="外部单号">
            <a-input
              v-model:value="form.externalOrderNo"
              size="large"
              placeholder="可选，不填则后端自动生成"
              allow-clear
            />
          </a-form-item>

          <a-form-item label="备注">
            <a-textarea
              v-model:value="form.remark"
              :rows="3"
              :maxlength="120"
              show-count
              placeholder="例如：送到 B2 后联系仓管"
            />
          </a-form-item>

          <div class="route-preview">
            <div class="preview-label">路线预览</div>
            <div class="preview-route">
              <span>{{ selectedPickup?.stationCode || '--' }}</span>
              <span class="preview-arrow">→</span>
              <span>{{ selectedDropoff?.stationCode || '--' }}</span>
            </div>
            <div class="preview-meta">
              <span>{{ selectedPickup?.stationName || '未选择取货点' }}</span>
              <span>{{ selectedDropoff?.stationName || '未选择送货点' }}</span>
            </div>
          </div>

          <button class="submit-btn" :disabled="submitting" @click.prevent="submitOrder">
            <span v-if="!submitting">提交并自动派车</span>
            <span v-else>提交中...</span>
          </button>
        </a-form>
      </section>

      <section v-if="lastCreatedOrder" class="result-card">
        <div class="section-head">
          <span>最近创建</span>
          <span class="result-state">{{ lastCreatedOrder.taskStatus || lastCreatedOrder.orderStatus }}</span>
        </div>
        <div class="result-grid">
          <div class="result-item">
            <label>订单号</label>
            <strong>{{ lastCreatedOrder.orderNo }}</strong>
          </div>
          <div class="result-item">
            <label>任务号</label>
            <strong>{{ lastCreatedOrder.taskNo || '--' }}</strong>
          </div>
          <div class="result-item">
            <label>车辆</label>
            <strong>{{ trackedVehicle?.vehicleCode || '--' }}</strong>
          </div>
          <div class="result-item">
            <label>结果</label>
            <strong>{{ lastCreatedOrder.message || '--' }}</strong>
          </div>
        </div>
      </section>

      <section class="order-feed">
        <div class="section-head">
          <span>实时订单</span>
          <span>{{ parkOrders.length }}</span>
        </div>
        <div class="feed-list">
          <button
            v-for="order in parkOrders.slice(0, 10)"
            :key="order.orderId"
            class="feed-card"
            :class="{ active: trackedOrderId === order.orderId }"
            @click="trackedOrderId = order.orderId"
          >
            <div class="feed-top">
              <strong>{{ order.orderNo }}</strong>
              <span class="feed-stage" :class="stageClass(order.runtimeStage)">
                {{ stageLabel(order.runtimeStage) }}
              </span>
            </div>
            <div class="feed-route">
              <span>{{ order.pickupStation.stationCode }}</span>
              <span>→</span>
              <span>{{ order.dropoffStation.stationCode }}</span>
            </div>
            <div class="feed-meta">
              <span>{{ order.vehicleCode || '待分配' }}</span>
              <span>{{ formatTime(order.updatedAt) }}</span>
            </div>
          </button>
          <div v-if="parkOrders.length === 0" class="feed-empty">暂无订单</div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { createParkOrder, getParkLayout, getParkOrders, getParkStations, getParkVehicles } from '@/api/park'
import type {
  ParkLayout,
  ParkOrderCreateRequest,
  ParkOrderCreateResponse,
  ParkOrderSnapshot,
  ParkStation,
  ParkVehicleSnapshot,
} from '@/types/park'

const loadingStations = ref(false)
const submitting = ref(false)
const stations = ref<ParkStation[]>([])
const vehicles = ref<ParkVehicleSnapshot[]>([])
const parkOrders = ref<ParkOrderSnapshot[]>([])
const parkLayout = ref<ParkLayout | null>(null)
const lastCreatedOrder = ref<ParkOrderCreateResponse | null>(null)
const trackedOrderId = ref<number | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

const form = reactive<ParkOrderCreateRequest>({
  externalOrderNo: '',
  pickupStationId: undefined as unknown as number,
  dropoffStationId: undefined as unknown as number,
  priority: 'P1',
  remark: '',
})

const priorities = [
  { value: 'P0', label: '最高优先' },
  { value: 'P1', label: '优先处理' },
  { value: 'P2', label: '标准配送' },
  { value: 'P3', label: '低优先级' },
]

const stationOptions = computed(() =>
  stations.value.map(station => ({
    value: station.stationId,
    label: `${station.stationCode} · ${station.stationName}`,
  })),
)

const dropoffOptions = computed(() =>
  stationOptions.value.filter(option => option.value !== form.pickupStationId),
)

const selectedPickup = computed(() =>
  stations.value.find(station => station.stationId === form.pickupStationId) || null,
)

const selectedDropoff = computed(() =>
  stations.value.find(station => station.stationId === form.dropoffStationId) || null,
)

const activeOrders = computed(() =>
  parkOrders.value.filter(order => !['COMPLETED', 'FAILED'].includes(order.runtimeStage)),
)

const trackedOrder = computed(() => {
  if (trackedOrderId.value) {
    const matched = parkOrders.value.find(order => order.orderId === trackedOrderId.value)
    if (matched) return matched
  }
  return activeOrders.value[0] || parkOrders.value[0] || null
})

const trackedVehicle = computed(() => {
  if (!trackedOrder.value?.vehicleId) return null
  return vehicles.value.find(vehicle => vehicle.vehicleId === trackedOrder.value?.vehicleId) || null
})

const currentTarget = computed(() => {
  if (!trackedOrder.value) return null
  const stage = trackedOrder.value.runtimeStage
  if (['TO_DROPOFF', 'HEADING_TO_DROPOFF', 'UNLOADING', 'COMPLETED'].includes(stage)) {
    return trackedOrder.value.dropoffStation
  }
  return trackedOrder.value.pickupStation
})

const currentTargetLabel = computed(() => {
  if (!trackedOrder.value || !currentTarget.value) return '--'
  const targetType = ['TO_DROPOFF', 'HEADING_TO_DROPOFF', 'UNLOADING', 'COMPLETED'].includes(trackedOrder.value.runtimeStage)
    ? '送货点'
    : '取货点'
  return `${targetType} ${currentTarget.value.stationCode}`
})

const timelineState = computed(() => {
  const stage = trackedOrder.value?.runtimeStage
  if (!stage) return 0
  if (stage === 'COMPLETED') return 4
  if (['TO_DROPOFF', 'HEADING_TO_DROPOFF', 'UNLOADING'].includes(stage)) return 3
  if (['TO_PICKUP', 'HEADING_TO_PICKUP', 'LOADING'].includes(stage)) return 2
  return 1
})

const routePolylinePoints = computed(() => {
  if (!trackedOrder.value) return ''
  return [
    `${trackedOrder.value.pickupStation.x},${svgY(trackedOrder.value.pickupStation.y)}`,
    `${trackedOrder.value.dropoffStation.x},${svgY(trackedOrder.value.dropoffStation.y)}`,
  ].join(' ')
})

const stageLabelMap: Record<string, string> = {
  PENDING_ASSIGNMENT: '待分配',
  HEADING_TO_PICKUP: '前往取货',
  TO_PICKUP: '前往取货',
  LOADING: '装货中',
  HEADING_TO_DROPOFF: '配送中',
  TO_DROPOFF: '配送中',
  UNLOADING: '卸货中',
  COMPLETED: '已完成',
  FAILED: '失败',
  MANUAL_PENDING: '人工介入',
}

function svgY(y: number) {
  return parkLayout.value ? parkLayout.value.height - y : y
}

function markerStyle(x: number, y: number) {
  if (!parkLayout.value) return {}
  return {
    left: `${(x / parkLayout.value.width) * 100}%`,
    top: `${(1 - y / parkLayout.value.height) * 100}%`,
  }
}

function stageLabel(stage: string) {
  return stageLabelMap[stage] || stage
}

function stageClass(stage: string) {
  if (stage === 'COMPLETED') return 'done'
  if (stage === 'FAILED' || stage === 'MANUAL_PENDING') return 'risk'
  if (stage === 'LOADING' || stage === 'UNLOADING') return 'hold'
  return 'run'
}

function formatTime(value: string | null) {
  if (!value) return '--'
  return dayjs(value).format('HH:mm:ss')
}

async function fetchStations() {
  loadingStations.value = true
  try {
    const response = await getParkStations()
    stations.value = response.data || []
    if (!form.pickupStationId && stations.value[0]) {
      form.pickupStationId = stations.value[0].stationId
    }
    if (!form.dropoffStationId && stations.value[1]) {
      form.dropoffStationId = stations.value[1].stationId
    }
  } finally {
    loadingStations.value = false
  }
}

async function fetchLayout() {
  const response = await getParkLayout()
  parkLayout.value = response.data
}

async function fetchOrders() {
  const response = await getParkOrders()
  parkOrders.value = response.data || []
  if (!trackedOrderId.value && parkOrders.value[0]) {
    trackedOrderId.value = parkOrders.value[0].orderId
  }
}

async function fetchVehicles() {
  const response = await getParkVehicles()
  vehicles.value = response.data || []
}

function validateForm() {
  if (!form.pickupStationId) {
    message.error('请选择取货站点')
    return false
  }
  if (!form.dropoffStationId) {
    message.error('请选择送货站点')
    return false
  }
  if (form.pickupStationId === form.dropoffStationId) {
    message.error('取货站点和送货站点不能相同')
    return false
  }
  return true
}

async function submitOrder() {
  if (!validateForm()) return
  submitting.value = true
  try {
    const response = await createParkOrder({
      externalOrderNo: form.externalOrderNo?.trim() || undefined,
      pickupStationId: form.pickupStationId,
      dropoffStationId: form.dropoffStationId,
      priority: form.priority || 'P1',
      remark: form.remark?.trim() || undefined,
    })
    lastCreatedOrder.value = response.data
    trackedOrderId.value = response.data.orderId
    message.success('订单已创建，手机端将自动开始追踪配送')
    form.externalOrderNo = ''
    form.remark = ''
    await Promise.all([fetchOrders(), fetchVehicles()])
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await Promise.all([fetchStations(), fetchLayout(), fetchOrders(), fetchVehicles()])
  pollTimer = setInterval(() => {
    fetchOrders()
    fetchVehicles()
  }, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped lang="less">
.mobile-order-page {
  height: 100vh;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  background:
    radial-gradient(circle at top left, rgba(62, 166, 255, 0.14), transparent 30%),
    radial-gradient(circle at top right, rgba(0, 214, 143, 0.1), transparent 26%),
    linear-gradient(180deg, #08111d 0%, #050913 100%);
  color: #d8e4f2;
}

.mobile-toolbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(62, 166, 255, 0.1);
  background: rgba(6, 12, 22, 0.9);
  backdrop-filter: blur(14px);
}

.toolbar-link {
  color: #58b6ff;
  text-decoration: none;
  font-size: 13px;
  font-weight: 600;
}

.toolbar-title {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.toolbar-eyebrow {
  color: #5a7a9a;
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.toolbar-title strong {
  color: #f4f8fc;
  font-size: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.toolbar-submit {
  min-width: 88px;
  height: 38px;
  padding: 0 14px;
  border: 1px solid rgba(62, 166, 255, 0.3);
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(62, 166, 255, 0.22), rgba(62, 166, 255, 0.08));
  color: #b8dcff;
  font-size: 13px;
  font-weight: 700;
  box-shadow: 0 8px 20px rgba(62, 166, 255, 0.12);
}

.toolbar-submit:disabled {
  opacity: 0.5;
}

.mobile-shell {
  width: min(100%, 560px);
  margin: 0 auto;
  padding: 18px 16px 48px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-card,
.tracking-card,
.order-card,
.result-card,
.order-feed {
  border: 1px solid rgba(62, 166, 255, 0.1);
  border-radius: 24px;
  background: rgba(6, 12, 22, 0.72);
  backdrop-filter: blur(14px);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.28);
}

.hero-card {
  padding: 22px 18px;
  background:
    linear-gradient(135deg, rgba(62, 166, 255, 0.1), transparent 42%),
    rgba(8, 14, 26, 0.8);
}

.hero-copy {
  max-width: 420px;
}

.hero-eyebrow {
  color: #58b6ff;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  font-size: 11px;
}

.hero-card h1 {
  margin: 10px 0 8px;
  font-size: 32px;
  line-height: 1;
  letter-spacing: -0.04em;
  color: #f4f8fc;
}

.hero-card p {
  margin: 0;
  color: #7d94aa;
  line-height: 1.6;
}

.hero-pills {
  display: flex;
  gap: 10px;
  margin-top: 16px;
  flex-wrap: wrap;
}

.hero-pill {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  border: 1px solid transparent;
}

.hero-pill.blue {
  color: #74c2ff;
  background: rgba(62, 166, 255, 0.12);
  border-color: rgba(62, 166, 255, 0.22);
}

.hero-pill.gold {
  color: #ffb020;
  background: rgba(255, 176, 32, 0.1);
  border-color: rgba(255, 176, 32, 0.2);
}

.hero-pill.green {
  color: #00d68f;
  background: rgba(0, 214, 143, 0.1);
  border-color: rgba(0, 214, 143, 0.2);
}

.tracking-card,
.order-card,
.result-card,
.order-feed {
  padding: 18px;
}

.section-head,
.feed-top,
.feed-meta,
.feed-route,
.tracking-summary,
.tracking-legend {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-head {
  margin-bottom: 14px;
  color: #eaf2fb;
  font-size: 15px;
  font-weight: 700;
}

.section-tip {
  color: #6f88a2;
  font-size: 11px;
  font-weight: 500;
}

.tracking-switch {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 8px;
  margin-bottom: 12px;
}

.tracking-chip {
  flex: 0 0 auto;
  height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(62, 166, 255, 0.12);
  background: rgba(6, 12, 22, 0.6);
  color: #7d94aa;
  font-size: 12px;
  font-weight: 700;
}

.tracking-chip.active {
  border-color: rgba(62, 166, 255, 0.35);
  background: rgba(62, 166, 255, 0.12);
  color: #74c2ff;
}

.tracking-map-shell {
  padding: 14px;
  border-radius: 20px;
  background: rgba(6, 12, 22, 0.5);
  border: 1px solid rgba(62, 166, 255, 0.08);
}

.tracking-map-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: 16px;
  overflow: hidden;
  background: #09101b;
}

.tracking-map-image,
.tracking-map-overlay {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.tracking-map-image {
  object-fit: cover;
  object-position: center;
}

.tracking-map-overlay {
  pointer-events: none;
}

.map-main-route {
  fill: none;
  stroke: url(#routeGradient);
  stroke-width: 14;
  stroke-linecap: round;
  stroke-linejoin: round;
  opacity: 0.92;
}

.map-vehicle-route {
  stroke: rgba(30, 102, 255, 0.72);
  stroke-width: 8;
  stroke-linecap: round;
  stroke-dasharray: 18 12;
}

.map-pin,
.map-vehicle-badge {
  position: absolute;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.map-pin span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 40px;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  border: 2px solid rgba(5, 9, 19, 0.9);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.35);
}

.map-pin.pickup span {
  background: #00d68f;
}

.map-pin.dropoff span {
  background: #ffb020;
}

.map-vehicle-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.vehicle-ring {
  position: absolute;
  width: 34px;
  height: 34px;
  border-radius: 999px;
  background: rgba(62, 166, 255, 0.18);
}

.vehicle-dot {
  position: relative;
  width: 18px;
  height: 18px;
  border-radius: 999px;
  background: #3ea6ff;
  border: 3px solid rgba(5, 9, 19, 0.9);
  box-shadow: 0 6px 16px rgba(62, 166, 255, 0.35);
}

.vehicle-code {
  position: relative;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(5, 9, 19, 0.88);
  border: 1px solid rgba(62, 166, 255, 0.3);
  color: #b8dcff;
  font-size: 11px;
  font-weight: 800;
  box-shadow: 0 6px 14px rgba(0, 0, 0, 0.3);
}

.tracking-legend {
  justify-content: flex-start;
  gap: 12px;
  margin-top: 10px;
  color: #6f88a2;
  font-size: 11px;
}

.legend-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  margin-right: 5px;
}

.legend-dot.pickup { background: #00d68f; }
.legend-dot.dropoff { background: #ffb020; }
.legend-dot.vehicle { background: #3ea6ff; }

.tracking-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.summary-item {
  padding: 12px;
  border-radius: 16px;
  background: rgba(6, 12, 22, 0.5);
  border: 1px solid rgba(62, 166, 255, 0.08);
}

.summary-item label {
  display: block;
  color: #6f88a2;
  font-size: 11px;
  margin-bottom: 6px;
}

.summary-item strong {
  display: block;
  color: #d8e4f2;
  font-size: 13px;
  line-height: 1.4;
  word-break: break-word;
}

.tracking-timeline {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 14px;
}

.timeline-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 10px 6px;
  border-radius: 16px;
  background: rgba(6, 12, 22, 0.4);
  color: #5a7a9a;
  font-size: 11px;
  text-align: center;
}

.timeline-step.active {
  color: #74c2ff;
  background: rgba(62, 166, 255, 0.1);
}

.step-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: currentColor;
}

.priority-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.priority-card {
  padding: 12px;
  border-radius: 16px;
  border: 1px solid rgba(62, 166, 255, 0.1);
  background: rgba(6, 12, 22, 0.5);
  color: #d8e4f2;
  text-align: left;
}

.priority-card.active {
  border-color: rgba(62, 166, 255, 0.4);
  background: linear-gradient(180deg, rgba(62, 166, 255, 0.14), rgba(62, 166, 255, 0.04));
}

.priority-value {
  display: block;
  font-family: 'JetBrains Mono', monospace;
  font-size: 16px;
  font-weight: 700;
}

.priority-label {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #6f88a2;
}

.route-preview {
  margin: 8px 0 18px;
  padding: 14px;
  border-radius: 18px;
  background: rgba(6, 12, 22, 0.45);
  border: 1px solid rgba(62, 166, 255, 0.08);
}

.preview-label {
  color: #58b6ff;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.preview-route {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: #eaf2fb;
}

.preview-arrow {
  color: #ffb020;
}

.preview-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  color: #6f88a2;
  font-size: 12px;
}

.submit-btn {
  width: 100%;
  height: 52px;
  border: 1px solid rgba(62, 166, 255, 0.3);
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(62, 166, 255, 0.2), rgba(62, 166, 255, 0.08));
  color: #b8dcff;
  font-size: 16px;
  font-weight: 800;
  letter-spacing: 0.02em;
  box-shadow: 0 10px 24px rgba(62, 166, 255, 0.12);
}

.submit-btn:disabled {
  opacity: 0.5;
}

.result-state,
.feed-stage {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.feed-stage.run {
  background: rgba(62, 166, 255, 0.12);
  color: #74c2ff;
}

.feed-stage.hold {
  background: rgba(255, 176, 32, 0.1);
  color: #ffb020;
}

.feed-stage.done,
.result-state {
  background: rgba(0, 214, 143, 0.1);
  color: #00d68f;
}

.feed-stage.risk {
  background: rgba(255, 77, 109, 0.1);
  color: #ff4d6d;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.result-item {
  padding: 12px;
  border-radius: 16px;
  background: rgba(6, 12, 22, 0.5);
  border: 1px solid rgba(62, 166, 255, 0.08);
}

.result-item label {
  display: block;
  color: #6f88a2;
  font-size: 11px;
  margin-bottom: 6px;
}

.result-item strong {
  display: block;
  color: #d8e4f2;
  font-size: 13px;
  line-height: 1.4;
  word-break: break-word;
}

.feed-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.feed-card {
  width: 100%;
  padding: 14px;
  border-radius: 18px;
  background: rgba(6, 12, 22, 0.45);
  border: 1px solid rgba(62, 166, 255, 0.08);
  text-align: left;
}

.feed-card.active {
  border-color: rgba(62, 166, 255, 0.3);
  box-shadow: inset 0 0 0 1px rgba(62, 166, 255, 0.1);
}

.feed-top strong {
  font-family: 'JetBrains Mono', monospace;
  color: #eaf2fb;
  font-size: 13px;
}

.feed-route {
  margin-top: 10px;
  justify-content: flex-start;
  gap: 8px;
  font-size: 15px;
  color: #9ab0c6;
}

.feed-meta {
  margin-top: 10px;
  color: #6f88a2;
  font-size: 12px;
}

.feed-empty {
  padding: 20px 12px;
  text-align: center;
  color: #5a7a9a;
}

:deep(.ant-form-item-label > label) {
  color: #9ab0c6;
}

:deep(.ant-select-selector) {
  background: rgba(6, 12, 22, 0.6) !important;
  border-color: rgba(62, 166, 255, 0.14) !important;
  color: #d8e4f2 !important;
}

:deep(.ant-select-selection-placeholder) {
  color: #5a7a9a !important;
}

:deep(.ant-select-selection-item) {
  color: #d8e4f2 !important;
}

:deep(.ant-select-arrow) {
  color: #6f88a2;
}

:deep(.ant-input),
:deep(.ant-input-affix-wrapper) {
  background: rgba(6, 12, 22, 0.6);
  border-color: rgba(62, 166, 255, 0.14);
  color: #d8e4f2;
}

:deep(.ant-input::placeholder) {
  color: #5a7a9a;
}

:deep(.ant-input-textarea) {
  background: transparent;
}

:deep(.ant-input-textarea .ant-input) {
  background: rgba(6, 12, 22, 0.6);
  border-color: rgba(62, 166, 255, 0.14);
  color: #d8e4f2;
}

:deep(.ant-input-textarea-show-count .ant-input-data-count) {
  color: #5a7a9a;
}

:deep(.ant-input-clear-icon) {
  color: #6f88a2;
}

:deep(.ant-select-dropdown) {
  background: rgba(8, 17, 29, 0.96);
  border: 1px solid rgba(62, 166, 255, 0.14);
}

:deep(.ant-select-item) {
  color: #d8e4f2;
}

:deep(.ant-select-item-option-active) {
  background: rgba(62, 166, 255, 0.1);
}

:deep(.ant-select-item-option-selected) {
  background: rgba(62, 166, 255, 0.18);
  color: #74c2ff;
}

@media (max-width: 420px) {
  .mobile-toolbar {
    grid-template-columns: 1fr auto;
    grid-template-areas:
      'title submit'
      'back back';
    align-items: start;
  }

  .toolbar-link {
    grid-area: back;
  }

  .toolbar-title {
    grid-area: title;
  }

  .toolbar-submit {
    grid-area: submit;
  }

  .mobile-shell {
    padding: 14px 12px 24px;
  }

  .hero-card h1 {
    font-size: 28px;
  }

  .priority-grid,
  .result-grid,
  .tracking-summary,
  .tracking-timeline {
    grid-template-columns: 1fr;
  }

  .preview-meta {
    flex-direction: column;
  }
}
</style>

<style lang="less">
.ant-select-dropdown {
  background: rgba(8, 17, 29, 0.96) !important;
  border: 1px solid rgba(62, 166, 255, 0.14) !important;
  backdrop-filter: blur(12px);
}

.ant-select-item {
  color: #d8e4f2 !important;
}

.ant-select-item-option-active {
  background: rgba(62, 166, 255, 0.1) !important;
}

.ant-select-item-option-selected {
  background: rgba(62, 166, 255, 0.18) !important;
  color: #74c2ff !important;
}

.ant-select-item-option-selected .ant-select-item-option-state {
  color: #74c2ff !important;
}

.ant-select-empty {
  color: #5a7a9a;
}
</style>
