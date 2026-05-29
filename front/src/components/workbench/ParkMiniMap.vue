<template>
  <div class="park-mini-map" ref="wrapRef">
    <canvas ref="canvasRef" class="map-canvas" />
    <div v-if="!layout" class="map-empty">地图加载中…</div>
    <div class="map-overlay">
      <span class="map-stat">{{ vehicles.length }} 车在线</span>
      <router-link to="/vehicle-tracking" class="map-link">全屏监控 →</router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import type { ParkLayout, ParkVehicleSnapshot } from '@/types/park'

const props = defineProps<{
  layout: ParkLayout | null
  vehicles: ParkVehicleSnapshot[]
  highlightTaskId?: number | null
}>()

const wrapRef = ref<HTMLDivElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
let resizeObserver: ResizeObserver | null = null

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

  ctx.fillStyle = '#0d1117'
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
    ctx.fillStyle = 'rgba(0, 180, 216, 0.15)'
    ctx.fillRect(tx(spot.x) - 4, ty(spot.y) - 4, 8, 8)
  }

  for (const station of layout.stations) {
    ctx.beginPath()
    ctx.fillStyle = station.area === 'A' ? '#00b4d8' : '#ffb703'
    ctx.arc(tx(station.x), ty(station.y), 5, 0, Math.PI * 2)
    ctx.fill()
  }

  for (const vehicle of props.vehicles) {
    const linked = props.highlightTaskId != null && vehicle.currentTaskId === props.highlightTaskId
    const x = tx(vehicle.x)
    const y = ty(vehicle.y)
    ctx.beginPath()
    ctx.fillStyle = linked ? '#00e676' : vehicle.onlineStatus === 'ONLINE' ? '#48cae4' : '#6e7681'
    ctx.strokeStyle = linked ? '#00e676' : 'rgba(255,255,255,0.2)'
    ctx.lineWidth = linked ? 2 : 1
    ctx.moveTo(x, y - 7)
    ctx.lineTo(x + 6, y + 5)
    ctx.lineTo(x - 6, y + 5)
    ctx.closePath()
    ctx.fill()
    ctx.stroke()
  }
}

watch(
  () => [props.layout, props.vehicles, props.highlightTaskId],
  () => draw(),
  { deep: true }
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
  background: #0d1117;
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

.map-overlay {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(13, 17, 23, 0.82);
  border: 1px solid rgba(48, 54, 61, 0.8);
  backdrop-filter: blur(6px);
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
    color: #7ee8ff;
  }
}
</style>
