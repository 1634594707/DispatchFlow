<template>
  <section class="tracking-panel">
    <div class="panel-head">
      <div>
        <span class="panel-title">{{ isCompleted ? '配送已完成' : '配送进行中' }}</span>
        <span class="panel-sub">{{ order.orderNo }}</span>
      </div>
      <span class="stage-badge" :class="stageClass(order.runtimeStage)">
        {{ stageLabel(order.runtimeStage) }}
      </span>
    </div>

    <div v-if="activeOrders.length > 1" class="order-switch">
      <button
        v-for="item in activeOrders.slice(0, 5)"
        :key="item.orderId"
        type="button"
        class="order-chip"
        :class="{ active: order.orderId === item.orderId }"
        @click="$emit('selectOrder', item.orderId)"
      >
        {{ item.orderNo }}
      </button>
    </div>

    <!--
      图层读数（§4 T2-a/T2-c）挂在 .map-shell 上：marker 画在高德 canvas 里、DOM 数不到，
      这组 data-* 就是 e2e 唯一的计数钩子。**放在常驻可见的容器上而不是单独一个小条**，
      因为单独的小条在两个 chip 都撤掉后会变成零高度（`toBeVisible()` 当场红），
      而放进行内 v-if 的地图容器又会在"没配高德 Key"时整个消失（CI 就是那种环境）。
      ⚠ 移动端这张图的判据是"设施必须为 0"（v14 钉 swap/charging/facility-points 三个 0）。
    -->
    <div
      class="map-shell"
      data-testid="tracking-map-legend"
      :data-vehicle-markers="layerSummary?.vehicles ?? -1"
      :data-position-unknown="layerSummary?.positionUnknown ?? -1"
      :data-swap-markers="layerSummary?.swap ?? -1"
      :data-charging-markers="layerSummary?.charging ?? -1"
      :data-facility-points="layerSummary?.facilityPoints ?? -1"
      :data-fence-flash="fenceFlash ? 'on' : 'off'"
    >
      <div v-if="routeAnomalyText" class="route-anomaly">{{ routeAnomalyText }}</div>

      <div v-if="geoMapAvailable" class="map-wrap geo">
        <AmapGeoMap
          class="geo-map"
          :center="mapCenter"
          :zoom="16"
          :markers="geoMarkers"
          :polylines="geoPolylines"
          :polygons="geoPolygons"
          :fit-view-points="fitViewPoints"
          :fit-view-on-change="Boolean(vehicle)"
          :show-level-switcher="false"
          :show-layer-switcher="false"
        />
      </div>
      <div v-else class="map-wrap geo-map-unconfigured" data-testid="tracking-map-unconfigured">
        <p>地理底图未配置：本页需要高德 JS Key（<code>VITE_AMAP_KEY</code> /
          <code>VITE_AMAP_SECURITY_CODE</code>）。</p>
      </div>

      <div class="map-meta">
        <span v-if="remainingLabel" class="eta-pill">{{ remainingLabel }}</span>
        <router-link v-if="order.vehicleId" class="screen-link" :to="screenLink"
          >大屏跟车 →</router-link
        >
      </div>

      <!--
        ⚠ "位置未知"必须显式报出来：那是接口没给真经纬度的车 —— 不画点（也不许拿像素 x/y 换算，
        §7.5 逐行契约），但更不能静默消失："少画 1 台"和"这台车没有位置"在页面上得长得不一样。
        车队规模与补能点数量不在这里报：那是运营侧叙事，看 PC 大屏。
      -->
      <p v-if="layerSummary && layerSummary.positionUnknown > 0" class="map-legend">
        <span class="legend-item warn">{{ layerSummary.positionUnknown }} 台位置未知</span>
      </p>

      <!-- 对外规格（§4 T2-d）：静态文案，`t_vehicle` 没有续航/容量列，见 constants/vehicleSpec.ts -->
      <p v-if="vehicleSpec" class="vehicle-spec" data-testid="vehicle-spec">{{ vehicleSpec }}</p>
    </div>

    <div v-if="order.estimatedArrivalTime" class="eta-info">
      <span class="eta-label">预计到达</span>
      <span class="eta-time">{{ formatTime(order.estimatedArrivalTime) }}</span>
    </div>

    <div class="summary-grid">
      <div class="summary-cell">
        <label>取货</label>
        <strong>{{ order.pickupStation.stationCode }}</strong>
        <span>{{ order.pickupStation.stationName }}</span>
      </div>
      <div class="summary-cell">
        <label>送货</label>
        <strong>{{ order.dropoffStation.stationCode }}</strong>
        <span>{{ order.dropoffStation.stationName }}</span>
      </div>
      <div class="summary-cell">
        <label>车辆</label>
        <strong>{{ order.vehicleCode || '待分配' }}</strong>
        <span>{{ currentTargetLabel }}</span>
      </div>
      <div class="summary-cell">
        <label>阶段</label>
        <strong>{{ stageLabel(order.runtimeStage) }}</strong>
        <span>{{ timelineHint }}</span>
      </div>
    </div>

    <div class="timeline">
      <div
        v-for="step in timelineSteps"
        :key="step.key"
        class="timeline-step"
        :class="{ active: step.active }"
      >
        <span class="dot" />
        <span>{{ step.label }}</span>
      </div>
    </div>

    <div v-if="isCompleted" class="completed-cta">
      <p>本单已送达，可继续下一单短驳配送。</p>
      <button type="button" class="cta-btn" @click="$emit('orderAgain')">再下一单</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AmapGeoMap from '@/components/map/AmapGeoMap.vue'
import { parkDeliveryStageLabel } from '@/constants/parkDelivery'
import type { GeoMapMarker, GeoMapPolygon, GeoMapPolyline } from '@/maps/types'
import type { ParkOrderSnapshot, ParkStation, ParkVehicleSnapshot } from '@/types/park'

/** 传给地图图层的 marker 计数（§4 T2-a/T2-c）：由页面算好，面板只负责把这行读数画出来。 */
interface TrackingLayerSummary {
  vehicles: number
  positionUnknown: number
  swap: number
  charging: number
  facilityPoints: number
}

const props = defineProps<{
  order: ParkOrderSnapshot
  activeOrders: ParkOrderSnapshot[]
  vehicle: ParkVehicleSnapshot | null
  geoMapAvailable: boolean
  mapCenter: [number, number]
  geoMarkers: GeoMapMarker[]
  geoPolylines: GeoMapPolyline[]
  geoPolygons: GeoMapPolygon[]
  fitViewPoints: [number, number][]
  routeAnomalyText: string | null
  screenLink: { path: string; query: Record<string, string> }
  remainingLabel?: string | null
  lastUpdatedLabel?: string | null
  connectionStale?: boolean
  /** 传给地图图层的 marker 计数（§4 T2-a/T2-c），缺省则不画这行读数。 */
  layerSummary?: TrackingLayerSummary | null
  /** 车辆对外规格文案（§4 T2-d）。 */
  vehicleSpec?: string | null
  /** 围栏描边正在闪（§4 T2-f）：只作为 data 属性透出，样式在图层侧。 */
  fenceFlash?: boolean
}>()

defineEmits<{
  selectOrder: [orderId: number]
  orderAgain: []
}>()

const isCompleted = computed(() => props.order.runtimeStage === 'COMPLETED')

const currentTarget = computed((): ParkStation | null => {
  const stage = props.order.runtimeStage
  if (['TO_DROPOFF', 'HEADING_TO_DROPOFF', 'UNLOADING', 'COMPLETED'].includes(stage)) {
    return props.order.dropoffStation
  }
  return props.order.pickupStation
})

const currentTargetLabel = computed(() => {
  if (!currentTarget.value) return '--'
  const toDropoff = ['TO_DROPOFF', 'HEADING_TO_DROPOFF', 'UNLOADING', 'COMPLETED'].includes(
    props.order.runtimeStage,
  )
  return `${toDropoff ? '送货' : '取货'} · ${currentTarget.value.stationCode}`
})

const timelineState = computed(() => {
  const stage = props.order.runtimeStage
  if (stage === 'COMPLETED') return 4
  if (['TO_DROPOFF', 'HEADING_TO_DROPOFF', 'UNLOADING'].includes(stage)) return 3
  if (['TO_PICKUP', 'HEADING_TO_PICKUP', 'LOADING'].includes(stage)) return 2
  return 1
})

const timelineSteps = computed(() => [
  { key: 'accepted', label: '已接单', active: timelineState.value >= 1 },
  { key: 'pickup', label: '前往取货', active: timelineState.value >= 2 },
  { key: 'delivery', label: '配送中', active: timelineState.value >= 3 },
  { key: 'done', label: '已送达', active: timelineState.value >= 4 },
])

const timelineHint = computed(() => {
  if (isCompleted.value) return '可继续下单'
  if (timelineState.value >= 3) return '沿道路前往送货点'
  if (timelineState.value >= 2) return '沿道路前往取货点'
  return '等待派车或已接单'
})

function stageLabel(stage: string) {
  return parkDeliveryStageLabel(stage)
}

function stageClass(stage: string) {
  if (stage === 'COMPLETED') return 'done'
  if (stage === 'FAILED' || stage === 'MANUAL_PENDING') return 'risk'
  if (stage === 'LOADING' || stage === 'UNLOADING') return 'hold'
  return 'run'
}

function formatTime(time: string): string {
  const date = new Date(time)
  if (Number.isNaN(date.getTime())) return time
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>

<style scoped lang="less">
.tracking-panel {
  padding: var(--fsd-space-4);
  border-block: 1px solid var(--fsd-border);
  background: var(--fsd-bg-base);
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  position: relative;
}

.panel-title {
  display: block;
  font-family: var(--fsd-font-display);
  font-size: 16px;
  font-weight: 600;
  color: var(--fsd-text-heading);
  letter-spacing: -0.015em;
}

.panel-sub {
  display: block;
  margin-top: 4px;
  font-family: var(--fsd-font-mono);
  font-size: 11px;
  color: var(--fsd-text-tertiary);
  letter-spacing: -0.01em;
}

.stage-badge {
  flex-shrink: 0;
  padding: 4px 8px;
  border-radius: var(--fsd-radius-sm);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.stage-badge.run {
  color: var(--fsd-accent);
  background: var(--fsd-accent-bg);
  border: 1px solid var(--fsd-accent-border);
}

.stage-badge.hold {
  color: var(--fsd-warning);
  background: var(--fsd-warning-bg);
  border: 1px solid rgba(194, 148, 64, 0.3);
}

.stage-badge.done {
  color: var(--fsd-success);
  background: var(--fsd-success-bg);
  border: 1px solid rgba(74, 154, 117, 0.3);
}

.stage-badge.risk {
  color: var(--fsd-error);
  background: var(--fsd-error-bg);
  border: 1px solid rgba(196, 88, 104, 0.3);
}

.tracking-freshness {
  margin: -4px 0 12px;
  font-size: 11px;
  color: var(--fsd-text-secondary);
}

.connection-alert {
  display: grid;
  gap: var(--fsd-space-1);
  margin-bottom: var(--fsd-space-3);
  padding: var(--fsd-space-2) var(--fsd-space-3);
  border-left: 3px solid var(--fsd-warning);
  background: var(--fsd-warning-bg);
  color: var(--fsd-warning);
  font-size: var(--fsd-text-xs);
}

.connection-alert strong {
  color: var(--fsd-warning);
}

.order-switch {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  margin-bottom: 12px;
  padding-bottom: 4px;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.order-chip {
  flex: 0 0 auto;
  min-height: var(--fsd-touch-target-min);
  padding: 0 12px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-bg-elevated);
  color: var(--fsd-text-secondary);
  font-family: var(--fsd-font-mono);
  font-size: 11px;
  font-weight: 600;
  transition:
    background-color var(--fsd-transition-base),
    border-color var(--fsd-transition-base),
    color var(--fsd-transition-base);

  &:hover {
    border-color: var(--fsd-border-active);
    color: var(--fsd-text-primary);
  }
}

.order-chip.active {
  border-color: var(--fsd-accent-border);
  background: var(--fsd-accent-selected);
  color: var(--fsd-accent-strong);
}

.map-shell {
  position: relative;
  padding: var(--fsd-space-2);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-md);
  background: var(--fsd-bg-deep);
}

.route-anomaly {
  position: absolute;
  top: var(--fsd-space-3);
  left: 50%;
  z-index: 5;
  max-width: calc(100% - 2 * var(--fsd-space-3));
  padding: 6px var(--fsd-space-3);
  border-left: 3px solid var(--fsd-error);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-error-bg);
  color: var(--fsd-error);
  font-size: 11px;
  font-weight: var(--fsd-font-semibold);
  text-align: center;
  transform: translateX(-50%);
}

.map-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 11;
  border-radius: var(--fsd-radius-md);
  overflow: hidden;
  background: var(--fsd-bg-deep);
}

.geo-map {
  width: 100%;
  height: 100%;
  min-height: 220px;
}

/* 没有地理底图时不再退回园区示意图（示意场景已随 §园区调度删除）：明说什么条件下才有图。 */
.geo-map-unconfigured {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--fsd-space-4);
  background: var(--fsd-bg-base);
  color: var(--fsd-text-secondary);
  font-size: 12px;
  line-height: 1.6;
  text-align: center;

  code {
    color: var(--fsd-accent);
  }
}

.map-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 12px;
}

/* 图层读数：点的颜色对齐 `stationLayers.ts` 的角色色，图例和地图图标才是同一件事。 */
.map-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-bg-deep);
  color: var(--fsd-text-secondary);
  font-family: var(--fsd-font-mono);
  font-size: 10px;
  font-weight: var(--fsd-font-medium);

  &::before {
    width: 7px;
    height: 7px;
    border-radius: var(--fsd-radius-full);
    background: currentColor;
    content: '';
  }
}

.legend-item.vehicles {
  color: var(--fsd-accent);
}

.legend-item.warn {
  border-color: rgba(194, 148, 64, 0.4);
  color: var(--fsd-warning);
}

.legend-item.swap {
  color: #ff6b35;
}

.vehicle-spec {
  margin: 10px 0 0;
  color: var(--fsd-text-tertiary);
  font-size: 11px;
  letter-spacing: 0;
}

.eta-pill {
  padding: 4px 8px;
  border: 1px solid rgba(74, 154, 117, 0.3);
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-success-bg);
  color: var(--fsd-success);
  font-size: 11px;
  font-weight: 600;
  font-family: var(--fsd-font-mono);
  letter-spacing: -0.01em;
}

.eta-info {
  display: flex;
  align-items: center;
  gap: var(--fsd-space-2);
  margin-top: var(--fsd-space-2);
  padding: var(--fsd-space-2) 0;
  border-block: 1px solid var(--fsd-border-split);
}

.eta-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--fsd-warning);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.eta-time {
  font-size: 13px;
  font-weight: 600;
  color: var(--fsd-text-heading);
  font-family: var(--fsd-font-mono);
  letter-spacing: -0.01em;
}

.screen-link {
  margin-left: auto;
  font-size: 12px;
  font-weight: 600;
  color: var(--fsd-accent);
  text-decoration: none;
  transition: color var(--fsd-transition-fast);

  &:hover {
    color: var(--fsd-accent-strong);
  }
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: var(--fsd-space-3);
  border-top: 1px solid var(--fsd-border-split);
  border-left: 1px solid var(--fsd-border-split);
}

.summary-cell {
  min-width: 0;
  padding: var(--fsd-space-3);
  border-right: 1px solid var(--fsd-border-split);
  border-bottom: 1px solid var(--fsd-border-split);
}

.summary-cell label {
  display: block;
  font-size: 10px;
  color: var(--fsd-text-tertiary);
  margin-bottom: 4px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  font-weight: 600;
}

.summary-cell strong {
  display: block;
  font-size: 14px;
  color: var(--fsd-text-primary);
  font-weight: 600;
  font-family: var(--fsd-font-display);
  letter-spacing: -0.015em;
}

.summary-cell span {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--fsd-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timeline {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px;
  margin-top: 14px;
  position: relative;

  &::before {
    content: '';
    position: absolute;
    top: 18px;
    left: 12%;
    right: 12%;
    height: 2px;
    background: var(--fsd-border);
    z-index: 0;
  }
}

.timeline-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 8px 4px;
  border-radius: var(--fsd-radius-sm);
  font-size: 10px;
  color: var(--fsd-text-tertiary);
  text-align: center;
  font-weight: 500;
  position: relative;
  z-index: 1;
  transition: color var(--fsd-transition-fast);
}

.timeline-step.active {
  color: var(--fsd-accent-strong);
  font-weight: var(--fsd-font-semibold);
}

.dot {
  width: 10px;
  height: 10px;
  border: 2px solid var(--fsd-border-active);
  border-radius: var(--fsd-radius-full);
  background: var(--fsd-bg-elevated);
  transition:
    background-color var(--fsd-transition-base),
    border-color var(--fsd-transition-base);
}

.timeline-step.active .dot {
  border-color: var(--fsd-accent);
  background: var(--fsd-accent);
}

.completed-cta {
  margin-top: var(--fsd-space-3);
  padding: var(--fsd-space-3);
  border-left: 3px solid var(--fsd-success);
  background: var(--fsd-success-bg);
}

.completed-cta p {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--fsd-text-secondary);
  line-height: 1.5;
}

.cta-btn {
  width: 100%;
  min-height: 48px;
  border: 0;
  border-radius: var(--fsd-radius-sm);
  background: var(--fsd-action-primary);
  color: var(--fsd-text-on-action);
  font-size: var(--fsd-text-base);
  font-weight: var(--fsd-font-semibold);
  letter-spacing: -0.005em;
  cursor: pointer;
  transition: background-color var(--fsd-transition-base);

  &:hover {
    background: var(--fsd-action-primary-hover);
  }

  &:active {
    background: var(--fsd-action-primary-active);
  }
}
</style>
