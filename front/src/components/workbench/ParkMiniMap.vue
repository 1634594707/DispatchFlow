<template>
  <div class="park-mini-map" ref="wrapRef">
    <canvas ref="canvasRef" class="map-canvas" />
    <div v-if="!layout" class="map-empty">地图加载中…</div>
    <div class="map-legend">
      <span class="legend-item"><i class="dot pickup" />取货</span>
      <span class="legend-item"><i class="dot dropoff" />送货</span>
      <span class="legend-item"><i class="dot express" />接驳</span>
      <span class="legend-item"><i class="dot warehouse" />发货仓库</span>
      <span class="legend-item"><i class="dot swap" />换电柜</span>
      <span class="legend-item"><i class="dot vehicle" />车辆</span>
      <label v-if="hasChargingStations" class="legend-toggle">
        <input v-model="showCharging" type="checkbox" />
        充电站
      </label>
    </div>
    <div class="map-overlay">
      <span class="map-stat"
        >{{ orderableCount }} 个发货/作业站点 · {{ swapCount }} 座换电柜 · {{ vehicles.length }} 车在线</span
      >
      <router-link to="/vehicle-tracking" class="map-link">全屏监控 →</router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { ParkLayout, ParkVehicleSnapshot } from '@/types/park'
import {
  filterWorkbenchSituationStations,
  filterMobileOrderStations,
  filterSchematicParkVehicles,
  workbenchStationColor,
} from '@/maps/stationLayers'

const props = defineProps<{
  layout: ParkLayout | null
  vehicles: ParkVehicleSnapshot[]
  highlightTaskId?: number | null
}>()

const wrapRef = ref<HTMLDivElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const showCharging = ref(false)
let resizeObserver: ResizeObserver | null = null

const visibleStations = computed(() => {
  if (!props.layout) return []
  return filterWorkbenchSituationStations(props.layout.stations, {
    showCharging: showCharging.value,
  })
})

/** 站点数一律**从数据算**，不再用写死的 `ZJF_ORDERABLE_STATION_COUNT=8`：
 *  设施模型 v2 把发货点收敛成一个总仓库后，那个常数会在屏幕上直接说谎。 */
const orderableCount = computed(() =>
  filterMobileOrderStations(props.layout?.stations ?? []).length,
)
const swapCount = computed(
  () => (props.layout?.stations ?? []).filter((s) => s.stationType === 'SWAP_CABINET').length,
)

const hasChargingStations = computed(() =>
  (props.layout?.stations ?? []).some((station) => station.stationType === 'CHARGING_STATION'),
)

function draw() {
  const wrap = wrapRef.value
  const canvas = canvasRef.value
  const layout = props.layout
  if (!wrap || !canvas || !layout) return

  const dpr = window.devicePixelRatio || 1
  const width = wrap.clientWidth
  const height = wrap.clientHeight
  if (width <= 0 || height <= 0) return

  canvas.width = width * dpr
  canvas.height = height * dpr
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`

  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, width, height)

  const pad = 24
  const scale = Math.min((width - pad * 2) / layout.width, (height - pad * 2) / layout.height)
  const offsetX = (width - layout.width * scale) / 2
  const offsetY = (height - layout.height * scale) / 2

  const tx = (x: number) => offsetX + x * scale
  const ty = (y: number) => offsetY + y * scale

  ctx.fillStyle = '#0B1018'
  ctx.fillRect(0, 0, width, height)

  ctx.strokeStyle = 'rgba(48, 54, 61, 0.9)'
  ctx.lineWidth = 1.5
  const nodeMap = new Map(layout.roadNodes.map((n) => [n.code, n]))
  for (const seg of layout.roadSegments) {
    const from = nodeMap.get(seg.from)
    const to = nodeMap.get(seg.to)
    if (!from || !to) continue
    ctx.beginPath()
    ctx.moveTo(tx(from.x), ty(from.y))
    ctx.lineTo(tx(to.x), ty(to.y))
    ctx.stroke()
  }

  for (const spot of layout.parkingSpots) {
    ctx.fillStyle = 'rgba(34, 199, 230, 0.15)'
    ctx.fillRect(tx(spot.x) - 4, ty(spot.y) - 4, 8, 8)
  }

  for (const station of visibleStations.value) {
    ctx.beginPath()
    ctx.fillStyle = workbenchStationColor(station)
    ctx.arc(tx(station.x), ty(station.y), 5, 0, Math.PI * 2)
    ctx.fill()
  }

  for (const vehicle of filterSchematicParkVehicles(props.vehicles)) {
    const linked = props.highlightTaskId != null && vehicle.currentTaskId === props.highlightTaskId
    const x = tx(vehicle.x)
    const y = ty(vehicle.y)
    ctx.beginPath()
    ctx.fillStyle = linked ? '#2DE08A' : vehicle.onlineStatus === 'ONLINE' ? '#22C7E6' : '#6B7787'
    ctx.strokeStyle = linked ? '#2DE08A' : 'rgba(255,255,255,0.2)'
    ctx.lineWidth = linked ? 2 : 1
    ctx.moveTo(x, y - 7)
    ctx.lineTo(x + 6, y + 5)
    ctx.lineTo(x - 6, y + 5)
    ctx.closePath()
    ctx.fill()
    ctx.stroke()
    if (vehicle.onlineStatus === 'ONLINE') {
      ctx.font = '9px JetBrains Mono, monospace'
      ctx.fillStyle = vehicle.lowBattery ? '#FF5C7C' : 'rgba(255,255,255,0.65)'
      ctx.textAlign = 'center'
      ctx.fillText(`${vehicle.batteryLevel}%`, x, y + 14)
    }
  }
}

watch(
  () => [props.layout, props.vehicles, props.highlightTaskId, showCharging.value],
  () => draw(),
  { deep: true },
)

onMounted(() => {
  draw()
  if (wrapRef.value) {
    resizeObserver = new ResizeObserver(() => draw())
    resizeObserver.observe(wrapRef.value)
  }
})

onUnmounted(() => {
  resizeObserver?.disconnect()
})
</script>

<style scoped lang="less">
.park-mini-map {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 280px;
  border-radius: 12px;
  overflow: hidden;
  background: var(--fsd-bg-deep);
  border: 1px solid var(--fsd-border);
}

.map-canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.map-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--fsd-text-tertiary);
  font-size: 13px;
}

.map-legend {
  position: absolute;
  top: 12px;
  left: 12px;
  right: 12px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  border-radius: var(--fsd-radius-md);
  border: 1px solid var(--fsd-border);
  background: var(--fsd-surface-overlay);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: var(--fsd-text-secondary);
}

.legend-toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: auto;
  font-size: 11px;
  color: var(--fsd-text-secondary);
  cursor: pointer;
  user-select: none;

  input {
    accent-color: var(--fsd-accent);
  }
}

.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;

  &.pickup {
    background: var(--fsd-accent);
  }
  &.dropoff {
    background: var(--fsd-warning);
  }
  &.express {
    background: var(--fsd-success);
  }
  /* 与 WORKBENCH_STATION_COLORS 同色：图例点和小地图上的圆点必须一眼对得上 */
  &.warehouse {
    background: #4cc9f0;
  }
  &.swap {
    background: #ff6b35;
  }
  &.vehicle {
    width: 0;
    height: 0;
    border-radius: 0;
    border-left: 4px solid transparent;
    border-right: 4px solid transparent;
    border-bottom: 7px solid var(--fsd-accent);
    background: none;
  }
}

.map-overlay {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: var(--fsd-radius-md);
  border: 1px solid var(--fsd-border);
  background: var(--fsd-surface-overlay);
}

.map-stat {
  font-size: 12px;
  color: var(--fsd-text-secondary);
  font-family: 'JetBrains Mono', monospace;
}

.map-link {
  font-size: 12px;
  color: var(--fsd-accent);
  text-decoration: none;

  &:hover {
    color: var(--fsd-accent);
  }
}
</style>
