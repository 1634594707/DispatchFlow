<template>
  <div class="zone-map" ref="wrapRef">
    <canvas
      ref="canvasRef"
      class="zone-canvas"
      @mousedown="onDown"
      @mousemove="onMove"
      @mouseup="onUp"
      @mouseleave="onUp"
    />
    <p class="zone-hint">在地图上拖拽框选区域，暂停派车进入该范围（ADMIN）</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import type { ParkLayout } from '@/types/park'
import type { TrafficPauseZone } from '@/types/traffic'

const props = defineProps<{
  layout: ParkLayout | null
  zones: TrafficPauseZone[]
}>()

const emit = defineEmits<{
  select: [zone: { minX: number; minY: number; maxX: number; maxY: number }]
}>()

const wrapRef = ref<HTMLDivElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)
let resizeObserver: ResizeObserver | null = null
let dragging = false
let startX = 0
let startY = 0
let currentX = 0
let currentY = 0

function draw() {
  const wrap = wrapRef.value
  const canvas = canvasRef.value
  const layout = props.layout
  if (!wrap || !canvas || !layout?.width || !layout.height) return

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

  const pad = 20
  const scale = Math.min((width - pad * 2) / layout.width, (height - pad * 2) / layout.height)
  const offsetX = (width - layout.width * scale) / 2
  const offsetY = (height - layout.height * scale) / 2
  const tx = (x: number) => offsetX + x * scale
  const ty = (y: number) => offsetY + y * scale
  const invX = (px: number) => (px - offsetX) / scale
  const invY = (py: number) => (py - offsetY) / scale

  ctx.fillStyle = '#0B1018'
  ctx.fillRect(0, 0, width, height)

  const nodeMap = new Map((layout.roadNodes || []).map((n) => [n.code, n]))
  ctx.strokeStyle = 'rgba(100, 180, 255, 0.35)'
  ctx.lineWidth = 1.5
  for (const seg of layout.roadSegments || []) {
    const from = nodeMap.get(seg.from)
    const to = nodeMap.get(seg.to)
    if (!from || !to) continue
    ctx.beginPath()
    ctx.moveTo(tx(from.x), ty(from.y))
    ctx.lineTo(tx(to.x), ty(to.y))
    ctx.stroke()
  }

  for (const zone of props.zones) {
    ctx.fillStyle = 'rgba(255, 61, 113, 0.18)'
    ctx.strokeStyle = 'rgba(255, 61, 113, 0.75)'
    ctx.lineWidth = 2
    ctx.fillRect(tx(zone.minX), ty(zone.minY), (zone.maxX - zone.minX) * scale, (zone.maxY - zone.minY) * scale)
    ctx.strokeRect(tx(zone.minX), ty(zone.minY), (zone.maxX - zone.minX) * scale, (zone.maxY - zone.minY) * scale)
  }

  if (dragging) {
    const x1 = Math.min(startX, currentX)
    const y1 = Math.min(startY, currentY)
    const x2 = Math.max(startX, currentX)
    const y2 = Math.max(startY, currentY)
    ctx.fillStyle = 'rgba(255, 193, 7, 0.15)'
    ctx.strokeStyle = 'rgba(255, 193, 7, 0.9)'
    ctx.fillRect(x1, y1, x2 - x1, y2 - y1)
    ctx.strokeRect(x1, y1, x2 - x1, y2 - y1)
    ;(canvas as any).__invX = invX
    ;(canvas as any).__invY = invY
  }
}

function onDown(e: MouseEvent) {
  if (!props.layout) return
  const rect = canvasRef.value?.getBoundingClientRect()
  if (!rect) return
  dragging = true
  startX = e.clientX - rect.left
  startY = e.clientY - rect.top
  currentX = startX
  currentY = startY
  draw()
}

function onMove(e: MouseEvent) {
  if (!dragging) return
  const rect = canvasRef.value?.getBoundingClientRect()
  if (!rect) return
  currentX = e.clientX - rect.left
  currentY = e.clientY - rect.top
  draw()
}

function onUp() {
  if (!dragging || !canvasRef.value || !props.layout) {
    dragging = false
    return
  }
  dragging = false
  const canvas = canvasRef.value
  const invX = (canvas as any).__invX as ((px: number) => number) | undefined
  const invY = (canvas as any).__invY as ((py: number) => number) | undefined
  if (!invX || !invY) return
  const x1 = Math.min(startX, currentX)
  const y1 = Math.min(startY, currentY)
  const x2 = Math.max(startX, currentX)
  const y2 = Math.max(startY, currentY)
  if (Math.abs(x2 - x1) < 8 || Math.abs(y2 - y1) < 8) {
    draw()
    return
  }
  emit('select', {
    minX: invX(x1),
    minY: invY(y1),
    maxX: invX(x2),
    maxY: invY(y2),
  })
  draw()
}

watch(() => [props.layout, props.zones], () => draw(), { deep: true })

onMounted(() => {
  draw()
  if (wrapRef.value) {
    resizeObserver = new ResizeObserver(() => draw())
    resizeObserver.observe(wrapRef.value)
  }
})

onUnmounted(() => resizeObserver?.disconnect())
</script>

<style scoped lang="less">
.zone-map {
  height: 280px;
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
  overflow: hidden;
  background: var(--fsd-bg-deep);
}

.zone-canvas {
  width: 100%;
  height: 240px;
  display: block;
  cursor: crosshair;
}

.zone-hint {
  margin: 0;
  padding: 8px 12px;
  font-size: 12px;
  color: var(--fsd-text-tertiary);
  border-top: 1px solid var(--fsd-border);
}
</style>
