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
}>(), {
  points: () => [],
  segments: () => [],
  height: 280,
})

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

  ctx.fillStyle = '#0d1117'
  ctx.fillRect(0, 0, width, height)

  const nodeMap = new Map(data.roadNodes.map((n) => [n.code, n]))
  ctx.strokeStyle = 'rgba(48, 54, 61, 0.8)'
  ctx.lineWidth = 1
  const segs = props.segments.length > 0
    ? props.segments
    : data.roadSegments.map((s) => ({ from: s.from, to: s.to, color: undefined as string | undefined }))
  for (const seg of segs) {
    const from = nodeMap.get(seg.from)
    const to = nodeMap.get(seg.to)
    if (!from || !to) continue
    ctx.strokeStyle = seg.color || 'rgba(48, 54, 61, 0.8)'
    ctx.beginPath()
    ctx.moveTo(tx(from.x), ty(from.y))
    ctx.lineTo(tx(to.x), ty(to.y))
    ctx.stroke()
  }

  for (const point of props.points) {
    ctx.beginPath()
    ctx.fillStyle = point.color || '#00b4d8'
    ctx.arc(tx(point.x), ty(point.y), 6, 0, Math.PI * 2)
    ctx.fill()
    if (point.label) {
      ctx.fillStyle = '#c9d1d9'
      ctx.font = '11px sans-serif'
      ctx.fillText(point.label, tx(point.x) + 8, ty(point.y) + 4)
    }
  }
}

watch(() => [props.parkId, props.points, props.segments, layout.value], () => {
  draw()
}, { deep: true })

onMounted(async () => {
  await loadLayout()
  if (canvasRef.value?.parentElement) {
    resizeObserver = new ResizeObserver(draw)
    resizeObserver.observe(canvasRef.value.parentElement)
  }
  draw()
})

onUnmounted(() => {
  resizeObserver?.disconnect()
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
