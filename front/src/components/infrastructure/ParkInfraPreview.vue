<template>
  <canvas ref="canvasRef" class="park-infra-preview" :style="{ height: `${height}px` }" />
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { getParkLayout } from '@/api/park'
import type { ParkLayout } from '@/types/park'

export interface InfraMapPoint {
  code: string
  label?: string
  x: number
  y: number
  color?: string
}

const props = withDefaults(defineProps<{
  parkId?: number
  points?: InfraMapPoint[]
  segments?: { from: string; to: string; color?: string }[]
  height?: number
  pickable?: boolean
  draggable?: boolean
}>(), {
  points: () => [],
  segments: () => [],
  height: 280,
  pickable: false,
  draggable: false,
})

const emit = defineEmits<{
  pick: [x: number, y: number]
  nodeMove: [code: string, x: number, y: number]
}>()

let lastTransform: { offsetX: number; offsetY: number; scale: number } | null = null
let dragging: { code: string; offsetX: number; offsetY: number } | null = null

const canvasRef = ref<HTMLCanvasElement | null>(null)
const layout = ref<ParkLayout | null>(null)
let resizeObserver: ResizeObserver | null = null

async function loadLayout() {
  try {
    const res = await getParkLayout(props.parkId)
    layout.value = res.data
  } catch {
    layout.value = null
  }
}

function screenToMap(px: number, py: number) {
  if (!lastTransform) return { x: 0, y: 0 }
  return {
    x: (px - lastTransform.offsetX) / lastTransform.scale,
    y: (py - lastTransform.offsetY) / lastTransform.scale,
  }
}

function hitTestNode(px: number, py: number): InfraMapPoint | null {
  if (!lastTransform) return null
  for (const point of props.points) {
    const dx = px - (lastTransform.offsetX + point.x * lastTransform.scale)
    const dy = py - (lastTransform.offsetY + point.y * lastTransform.scale)
    if (Math.hypot(dx, dy) <= 10) return point
  }
  return null
}

function draw() {
  const canvas = canvasRef.value
  const data = layout.value
  if (!canvas || !data) return

  const wrap = canvas.parentElement
  if (!wrap) return
  const width = wrap.clientWidth
  const height = props.height
  const dpr = window.devicePixelRatio || 1
  canvas.width = width * dpr
  canvas.height = height * dpr
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`

  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, width, height)

  const pad = 20
  const scale = Math.min((width - pad * 2) / data.width, (height - pad * 2) / data.height)
  const offsetX = (width - data.width * scale) / 2
  const offsetY = (height - data.height * scale) / 2
  const tx = (x: number) => offsetX + x * scale
  const ty = (y: number) => offsetY + y * scale
  lastTransform = { offsetX, offsetY, scale }

  ctx.fillStyle = '#0B1018'
  ctx.fillRect(0, 0, width, height)

  const nodeMap = new Map(data.roadNodes.map((n) => [n.code, n]))
  const segs = props.segments.length > 0
    ? props.segments
    : data.roadSegments.map((s) => ({ from: s.from, to: s.to, color: undefined as string | undefined }))
  for (const seg of segs) {
    const from = nodeMap.get(seg.from) ?? props.points.find((p) => p.code === seg.from)
    const to = nodeMap.get(seg.to) ?? props.points.find((p) => p.code === seg.to)
    if (!from || !to) continue
    const fx = 'x' in from ? from.x : (from as { x: number }).x
    const fy = 'y' in from ? from.y : (from as { y: number }).y
    const tx2 = 'x' in to ? to.x : (to as { x: number }).x
    const ty2 = 'y' in to ? to.y : (to as { y: number }).y
    ctx.strokeStyle = seg.color || 'rgba(48, 54, 61, 0.8)'
    ctx.beginPath()
    ctx.moveTo(tx(fx), ty(fy))
    ctx.lineTo(tx(tx2), ty(ty2))
    ctx.stroke()
  }

  for (const point of props.points) {
    ctx.beginPath()
    ctx.fillStyle = dragging?.code === point.code ? '#FFC04D' : (point.color || '#22C7E6')
    ctx.arc(tx(point.x), ty(point.y), 6, 0, Math.PI * 2)
    ctx.fill()
    if (point.label) {
      ctx.fillStyle = '#9BA8B8'
      ctx.font = '11px sans-serif'
      ctx.fillText(point.label, tx(point.x) + 8, ty(point.y) + 4)
    }
  }
}

watch(() => [props.parkId, props.points, props.segments, layout.value], () => {
  draw()
}, { deep: true })

function onCanvasClick(event: MouseEvent) {
  if (dragging || !props.pickable || !lastTransform || !canvasRef.value) return
  const rect = canvasRef.value.getBoundingClientRect()
  const { x, y } = screenToMap(event.clientX - rect.left, event.clientY - rect.top)
  emit('pick', Math.round(x * 10) / 10, Math.round(y * 10) / 10)
}

function onMouseDown(event: MouseEvent) {
  if (!props.draggable || !canvasRef.value) return
  const rect = canvasRef.value.getBoundingClientRect()
  const px = event.clientX - rect.left
  const py = event.clientY - rect.top
  const hit = hitTestNode(px, py)
  if (!hit) return
  dragging = { code: hit.code, offsetX: px, offsetY: py }
}

function onMouseMove(event: MouseEvent) {
  if (!dragging || !canvasRef.value || !lastTransform) return
  const rect = canvasRef.value.getBoundingClientRect()
  const { x, y } = screenToMap(event.clientX - rect.left, event.clientY - rect.top)
  emit('nodeMove', dragging.code, Math.round(x * 10) / 10, Math.round(y * 10) / 10)
  draw()
}

function onMouseUp() {
  dragging = null
  draw()
}

onMounted(async () => {
  await loadLayout()
  if (canvasRef.value?.parentElement) {
    resizeObserver = new ResizeObserver(draw)
    resizeObserver.observe(canvasRef.value.parentElement)
  }
  const canvas = canvasRef.value
  if (canvas) {
    if (props.pickable) {
      canvas.style.cursor = 'crosshair'
      canvas.addEventListener('click', onCanvasClick)
    }
    if (props.draggable) {
      canvas.style.cursor = 'grab'
      canvas.addEventListener('mousedown', onMouseDown)
      window.addEventListener('mousemove', onMouseMove)
      window.addEventListener('mouseup', onMouseUp)
    }
  }
  draw()
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  canvasRef.value?.removeEventListener('click', onCanvasClick)
  canvasRef.value?.removeEventListener('mousedown', onMouseDown)
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
})

watch(() => props.parkId, loadLayout)
</script>

<style scoped>
.park-infra-preview {
  width: 100%;
  border-radius: 8px;
  border: 1px solid var(--fsd-border);
  display: block;
}
</style>
