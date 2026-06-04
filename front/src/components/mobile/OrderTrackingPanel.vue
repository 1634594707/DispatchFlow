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
        <router-link v-if="order.vehicleId" class="screen-link" :to="screenLink">大屏跟车 →</router-link>
      </div>
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
      <div v-for="step in timelineSteps" :key="step.key" class="timeline-step" :class="{ active: step.active }">
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
</script>

<style scoped lang="less">
.tracking-panel {
  padding: 16px;
  border-radius: 22px;
  border: 1px solid rgba(255, 183, 3, 0.14);
  background:
    linear-gradient(160deg, rgba(255, 183, 3, 0.06), transparent 40%),
    rgba(6, 12, 22, 0.82);
  box-shadow: 0 18px 44px rgba(0, 0, 0, 0.28);
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-title {
  display: block;
  font-size: 16px;
  font-weight: 800;
  color: #f4f8fc;
}

.panel-sub {
  display: block;
  margin-top: 4px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: #6f88a2;
}

.stage-badge {
  flex-shrink: 0;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.stage-badge.run {
  color: #74c2ff;
  background: rgba(62, 166, 255, 0.14);
}

.stage-badge.hold {
  color: #ffb703;
  background: rgba(255, 183, 3, 0.12);
}

.stage-badge.done {
  color: #06d6a0;
  background: rgba(6, 214, 160, 0.12);
}

.stage-badge.risk {
  color: #ff6b8a;
  background: rgba(255, 107, 138, 0.12);
}

.order-switch {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  margin-bottom: 12px;
  padding-bottom: 4px;
}

.order-chip {
  flex: 0 0 auto;
  height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(62, 166, 255, 0.12);
  background: rgba(6, 12, 22, 0.55);
  color: #7d94aa;
  font-size: 11px;
  font-weight: 700;
}

.order-chip.active {
  border-color: rgba(255, 183, 3, 0.35);
  background: rgba(255, 183, 3, 0.1);
  color: #ffd166;
}

.map-shell {
  position: relative;
  padding: 10px;
  border-radius: 18px;
  background: rgba(4, 8, 16, 0.72);
  border: 1px solid rgba(62, 166, 255, 0.08);
}

.route-anomaly {
  position: absolute;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 5;
  padding: 6px 12px;
  border-radius: 8px;
  background: rgba(255, 77, 109, 0.92);
  color: #fff;
  font-size: 11px;
  max-width: calc(100% - 32px);
  text-align: center;
}

.map-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 11;
  border-radius: 14px;
  overflow: hidden;
  background: #09101b;
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
  stroke: #3ea6ff;
  stroke-width: 12;
  stroke-linecap: round;
  opacity: 0.85;
}

.vehicle-line {
  stroke: rgba(255, 183, 3, 0.75);
  stroke-width: 6;
  stroke-dasharray: 14 10;
}

.pin {
  position: absolute;
  transform: translate(-50%, -50%);
  width: 28px;
  height: 28px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 800;
  color: #fff;
  border: 2px solid rgba(5, 9, 19, 0.9);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.35);
}

.pin.pickup { background: #06d6a0; }
.pin.dropoff { background: #ffb703; }
.pin.vehicle { background: #3ea6ff; }

.map-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 10px;
}

.eta-pill {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(6, 214, 160, 0.1);
  border: 1px solid rgba(6, 214, 160, 0.22);
  color: #06d6a0;
  font-size: 11px;
  font-weight: 700;
}

.screen-link {
  margin-left: auto;
  font-size: 12px;
  font-weight: 700;
  color: #58b6ff;
  text-decoration: none;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 14px;
}

.summary-cell {
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(6, 12, 22, 0.45);
  border: 1px solid rgba(62, 166, 255, 0.06);
}

.summary-cell label {
  display: block;
  font-size: 10px;
  color: #6f88a2;
  margin-bottom: 4px;
}

.summary-cell strong {
  display: block;
  font-size: 13px;
  color: #eaf2fb;
}

.summary-cell span {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: #5a7a9a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timeline {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  margin-top: 12px;
}

.timeline-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 8px 4px;
  border-radius: 12px;
  font-size: 10px;
  color: #5a7a9a;
  text-align: center;
}

.timeline-step.active {
  color: #ffd166;
  background: rgba(255, 183, 3, 0.08);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: currentColor;
}

.completed-cta {
  margin-top: 14px;
  padding: 14px;
  border-radius: 16px;
  background: rgba(6, 214, 160, 0.06);
  border: 1px solid rgba(6, 214, 160, 0.16);
}

.completed-cta p {
  margin: 0 0 10px;
  font-size: 13px;
  color: #8fb4d9;
  line-height: 1.5;
}

.cta-btn {
  width: 100%;
  height: 44px;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #06d6a0, #00b4d8);
  color: #041018;
  font-size: 14px;
  font-weight: 800;
}
</style>
