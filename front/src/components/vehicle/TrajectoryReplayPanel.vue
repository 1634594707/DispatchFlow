<template>
  <div class="trajectory-replay">
    <a-radio-group v-model:value="source" size="small" @change="load">
      <a-radio-button value="realtime">实时尾迹</a-radio-button>
      <a-radio-button value="history">历史回放</a-radio-button>
    </a-radio-group>
    <div v-if="source === 'history'" class="replay-controls">
      <a-range-picker v-model:value="range" show-time size="small" @change="load" />
      <a-space>
        <a-button size="small" :disabled="points.length === 0" @click="togglePlay">
          {{ playing ? '暂停' : '播放' }}
        </a-button>
        <a-select v-model:value="speed" size="small" style="width: 88px" :options="speedOptions" />
      </a-space>
      <a-slider v-model:value="playIndex" :min="0" :max="Math.max(0, points.length - 1)" />
    </div>
    <canvas ref="canvasRef" class="replay-canvas" height="320" />
    <p class="replay-hint">
      {{ source === 'history' ? '历史回放（数据库遥测）' : '实时尾迹（Redis 滑动窗口，非历史归档）' }}
      · {{ displayPoints.length }} 点 · 异常停留 {{ dwellPoints.length }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import dayjs, { type Dayjs } from 'dayjs'
import { fetchVehicleTrajectory, fetchVehicleTrajectoryDwell, type VehicleTrajectoryDwell, type VehicleTrajectoryPoint } from '@/api/vehicle'

const props = defineProps<{ vehicleId: number }>()

const source = ref<'realtime' | 'history'>('realtime')
const range = ref<[Dayjs, Dayjs]>([dayjs().subtract(6, 'hour'), dayjs()])
const points = ref<VehicleTrajectoryPoint[]>([])
const dwellPoints = ref<VehicleTrajectoryDwell[]>([])
const playIndex = ref(0)
const playing = ref(false)
const speed = ref(1)
const canvasRef = ref<HTMLCanvasElement | null>(null)
let timer: ReturnType<typeof setInterval> | null = null

const speedOptions = [
  { label: '1x', value: 1 },
  { label: '2x', value: 2 },
  { label: '4x', value: 4 },
]

const displayPoints = computed(() => {
  if (source.value !== 'history' || !playing.value) {
    return points.value
  }
  return points.value.slice(0, playIndex.value + 1)
})

async function load() {
  const [from, to] = range.value
  const res = await fetchVehicleTrajectory(props.vehicleId, {
    source: source.value,
    from: source.value === 'history' ? from.toISOString() : undefined,
    to: source.value === 'history' ? to.toISOString() : undefined,
  })
  points.value = res.data || []
  if (source.value === 'history') {
    const dwellRes = await fetchVehicleTrajectoryDwell(props.vehicleId, {
      from: from.toISOString(),
      to: to.toISOString(),
    })
    dwellPoints.value = dwellRes.data || []
  } else {
    dwellPoints.value = []
  }
  playIndex.value = 0
  draw()
}

function togglePlay() {
  if (points.value.length === 0) return
  playing.value = !playing.value
  if (timer) clearInterval(timer)
  if (!playing.value) return
  timer = setInterval(() => {
    if (playIndex.value >= points.value.length - 1) {
      playing.value = false
      if (timer) clearInterval(timer)
      return
    }
    playIndex.value += 1
    draw()
  }, 400 / speed.value)
}

function draw() {
  const canvas = canvasRef.value
  const pts = displayPoints.value.filter((p) => p.x != null && p.y != null)
  if (!canvas || pts.length === 0) return
  const wrap = canvas.parentElement
  if (!wrap) return
  const width = wrap.clientWidth
  const height = 320
  const dpr = window.devicePixelRatio || 1
  canvas.width = width * dpr
  canvas.height = height * dpr
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, width, height)
  ctx.fillStyle = '#0d1117'
  ctx.fillRect(0, 0, width, height)
  const xs = pts.map((p) => p.x!)
  const ys = pts.map((p) => p.y!)
  const minX = Math.min(...xs)
  const maxX = Math.max(...xs)
  const minY = Math.min(...ys)
  const maxY = Math.max(...ys)
  const pad = 24
  const scale = Math.min((width - pad * 2) / (maxX - minX || 1), (height - pad * 2) / (maxY - minY || 1))
  const tx = (x: number) => pad + (x - minX) * scale
  const ty = (y: number) => height - pad - (y - minY) * scale
  ctx.strokeStyle = '#00B4D8'
  ctx.lineWidth = 2
  ctx.beginPath()
  pts.forEach((p, i) => {
    const x = tx(p.x!)
    const y = ty(p.y!)
    if (i === 0) ctx.moveTo(x, y)
    else ctx.lineTo(x, y)
  })
  ctx.stroke()
  for (const dwell of dwellPoints.value) {
    ctx.fillStyle = 'rgba(255, 170, 0, 0.35)'
    ctx.strokeStyle = '#FFAA00'
    ctx.beginPath()
    ctx.arc(tx(dwell.x), ty(dwell.y), 10, 0, Math.PI * 2)
    ctx.fill()
    ctx.stroke()
  }
  const last = pts[pts.length - 1]
  ctx.fillStyle = '#FF3D71'
  ctx.beginPath()
  ctx.arc(tx(last.x!), ty(last.y!), 5, 0, Math.PI * 2)
  ctx.fill()
}

watch(speed, () => {
  if (playing.value) {
    playing.value = false
    if (timer) clearInterval(timer)
    togglePlay()
  }
})

watch(displayPoints, () => draw(), { deep: true })

onMounted(() => load())
onUnmounted(() => { if (timer) clearInterval(timer) })

defineExpose({ reload: load })
</script>

<style scoped>
.trajectory-replay {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.replay-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
.replay-canvas {
  width: 100%;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.replay-hint {
  font-size: 12px;
  color: #6b7c8f;
  margin: 0;
}
</style>
