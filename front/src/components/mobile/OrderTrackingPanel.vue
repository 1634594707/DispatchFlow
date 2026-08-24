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

    <div class="map-shell">
      <div v-if="routeAnomalyText" class="route-anomaly">{{ routeAnomalyText }}</div>

      <div v-if="geoMapAvailable && !forceSchematicMap" class="map-wrap geo">
        <AmapGeoMap
          class="geo-map"
          :center="mapCenter"
          :zoom="16"
          :markers="geoMarkers"
          :polylines="geoPolylines"
          :polygons="geoPolygons"
          :fit-view-points="fitViewPoints"
          :fit-view-on-change="Boolean(vehicle)"
        />
      </div>
      <div v-else-if="parkLayout" class="map-wrap schematic">
        <img src="/park-map.svg" alt="园区示意" class="map-image" />
        <svg
          class="map-overlay"
          :viewBox="`0 0 ${parkLayout.width} ${parkLayout.height}`"
          preserveAspectRatio="none"
        >
          <polyline :points="schematicRoutePoints" class="route-line" />
          <line
            v-if="vehicle && currentTarget"
            :x1="vehicle.x"
            :y1="svgY(vehicle.y)"
            :x2="currentTarget.x"
            :y2="svgY(currentTarget.y)"
            class="vehicle-line"
          />
        </svg>
        <div class="pin pickup" :style="pinStyle(order.pickupStation.x, order.pickupStation.y)">
          取
        </div>
        <div class="pin dropoff" :style="pinStyle(order.dropoffStation.x, order.dropoffStation.y)">
          送
        </div>
        <div v-if="vehicle" class="pin vehicle" :style="pinStyle(vehicle.x, vehicle.y)">车</div>
      </div>

      <div class="map-meta">
        <span v-if="remainingLabel" class="eta-pill">{{ remainingLabel }}</span>
        <router-link v-if="order.vehicleId" class="screen-link" :to="screenLink"
          >大屏跟车 →</router-link
        >
      </div>
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
import type { ParkLayout, ParkOrderSnapshot, ParkStation, ParkVehicleSnapshot } from '@/types/park'

const props = defineProps<{
  order: ParkOrderSnapshot
  activeOrders: ParkOrderSnapshot[]
  vehicle: ParkVehicleSnapshot | null
  parkLayout: ParkLayout | null
  geoMapAvailable: boolean
  forceSchematicMap?: boolean
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

const schematicRoutePoints = computed(() => {
  const pickup = props.order.pickupStation
  const dropoff = props.order.dropoffStation
  return `${pickup.x},${svgY(pickup.y)} ${dropoff.x},${svgY(dropoff.y)}`
})

function svgY(y: number) {
  return props.parkLayout ? props.parkLayout.height - y : y
}

function pinStyle(x: number, y: number) {
  if (!props.parkLayout) return {}
  return {
    left: `${(x / props.parkLayout.width) * 100}%`,
    top: `${(1 - y / props.parkLayout.height) * 100}%`,
  }
}

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

.map-image,
.map-overlay {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.map-image {
  object-fit: cover;
}

.map-overlay {
  pointer-events: none;
}

.route-line {
  fill: none;
  stroke: var(--fsd-accent);
  stroke-width: 12;
  stroke-linecap: round;
  opacity: 0.85;
}

.vehicle-line {
  stroke: rgba(251, 191, 36, 0.75);
  stroke-width: 6;
  stroke-dasharray: 14 10;
}

.pin {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 2px solid var(--fsd-bg-base);
  border-radius: var(--fsd-radius-full);
  color: var(--fsd-text-on-action);
  font-family: var(--fsd-font-sans);
  font-size: 11px;
  font-weight: var(--fsd-font-semibold);
  transform: translate(-50%, -50%);
}

.pin.pickup {
  background: var(--fsd-success);
}
.pin.dropoff {
  background: var(--fsd-warning);
}
.pin.vehicle {
  background: var(--fsd-accent);
}

.map-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 12px;
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
