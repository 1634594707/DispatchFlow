<template>
  <div class="hourly-chart">
    <div class="chart-head">
      <span class="chart-title">{{ title }}</span>
      <span v-if="unit" class="chart-unit">{{ unit }}</span>
      <div class="chart-legend">
        <span v-for="s in series" :key="s.key" class="legend-item">
          <i class="legend-swatch" :style="{ background: s.color, opacity: s.kind === 'area' ? 0.4 : 1 }" />
          <span class="legend-text">{{ s.label }}</span>
        </span>
      </div>
    </div>

    <svg
      class="chart-svg"
      :viewBox="`0 0 ${W} ${H}`"
      preserveAspectRatio="xMidYMid meet"
      role="img"
      :aria-label="ariaLabel"
    >
      <!-- 横向网格 + Y 轴刻度 -->
      <g>
        <line
          v-for="tick in yTicks"
          :key="`g${tick.v}`"
          class="grid-line"
          :x1="PAD.l"
          :x2="W - PAD.r"
          :y1="tick.y"
          :y2="tick.y"
        />
        <text
          v-for="tick in yTicks"
          :key="`t${tick.v}`"
          class="axis-text"
          :x="PAD.l - 6"
          :y="tick.y + 3"
          text-anchor="end"
        >
          {{ tick.label }}
        </text>
      </g>

      <!-- 参考线（配置阈值）；超出图示范围时改由下方文案说明 -->
      <g v-if="referenceLine">
        <line
          class="ref-line"
          :x1="PAD.l"
          :x2="W - PAD.r"
          :y1="referenceLine.y"
          :y2="referenceLine.y"
        />
        <text class="ref-text" :x="W - PAD.r" :y="referenceLine.y - 4" text-anchor="end">
          {{ referenceLine.label }}
        </text>
      </g>

      <!-- 数据系列 -->
      <g v-for="rs in renderSeries" :key="rs.key">
        <path
          v-for="(d, idx) in rs.areas"
          :key="`a${idx}`"
          :d="d"
          :fill="rs.color"
          fill-opacity="0.16"
          stroke="none"
        />
        <path
          v-for="(d, idx) in rs.lines"
          :key="`l${idx}`"
          :d="d"
          fill="none"
          :stroke="rs.color"
          stroke-width="1.6"
          stroke-linejoin="round"
          stroke-linecap="round"
        />
      </g>

      <!-- 当前小时标记 -->
      <g v-if="nowX !== null">
        <line class="now-line" :x1="nowX" :x2="nowX" :y1="PAD.t" :y2="H - PAD.b" />
        <text
          class="now-text"
          :x="nowX + (nowAnchor === 'end' ? -4 : 4)"
          :y="PAD.t + 9"
          :text-anchor="nowAnchor"
        >
          现在
        </text>
      </g>

      <!-- X 轴小时刻度 -->
      <text
        v-for="h in X_TICKS"
        :key="`x${h}`"
        class="axis-text"
        :x="xOf(h)"
        :y="H - PAD.b + 14"
        text-anchor="middle"
      >
        {{ h }}
      </text>
    </svg>

    <p v-if="referenceOutOfRange" class="chart-note">
      配置阈值 {{ fmt(reference?.value ?? 0) }}{{ unit ? ` ${unit}` : '' }} 超出本图纵轴范围（纵轴上限
      {{ fmt(scaleMax) }}），未绘制参考线。
    </p>
  </div>
</template>

<script lang="ts">
/** 24 小时曲线的一个数据系列。 */
export interface HourlySeriesInput {
  key: string
  label: string
  color: string
  /** area：填充到底线；line：仅折线。默认 line。 */
  kind?: 'line' | 'area'
  /** 按 hourOfDay(0-23) 索引的 24 个槽位；缺测为 null，折线在缺口处断开。 */
  values: (number | null)[]
}
</script>

<script setup lang="ts">
import { computed } from 'vue'

interface Pt {
  i: number
  v: number
}

const props = withDefaults(
  defineProps<{
    title: string
    unit?: string
    series: HourlySeriesInput[]
    reference?: { value: number; label: string } | null
    /** 服务器当前小时（0-23），用于标注"现在"竖线；为空则不标注。 */
    currentHour?: number | null
  }>(),
  { unit: '', reference: null, currentHour: null },
)

const W = 680
const H = 200
const PAD = { l: 46, r: 14, t: 14, b: 22 }
const INNER_W = W - PAD.l - PAD.r
const INNER_H = H - PAD.t - PAD.b
const BASELINE = PAD.t + INNER_H
const X_TICKS = [0, 4, 8, 12, 16, 20, 23]

function round(v: number) {
  return Math.round(v * 10) / 10
}

/** 取"好看"的纵轴上限：1/2/2.5/5/10 × 10^n。 */
function niceCeil(v: number) {
  if (!Number.isFinite(v) || v <= 0) return 1
  const exp = Math.floor(Math.log10(v))
  const base = Math.pow(10, exp)
  const n = v / base
  const step = n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10
  return step * base
}

function fmt(v: number) {
  if (!Number.isFinite(v)) return '-'
  const abs = Math.abs(v)
  if (abs >= 1000) return v.toFixed(0)
  if (abs >= 100) return v.toFixed(1).replace(/\.0$/, '')
  if (abs >= 10) return v.toFixed(2).replace(/\.?0+$/, '')
  return v.toFixed(3).replace(/\.?0+$/, '')
}

const dataMax = computed(() => {
  let m = 0
  for (const s of props.series) {
    for (const v of s.values) {
      if (v != null && Number.isFinite(v)) m = Math.max(m, v)
    }
  }
  return m
})

const scaleMax = computed(() => niceCeil(dataMax.value))

function yOf(v: number) {
  const ratio = scaleMax.value <= 0 ? 0 : v / scaleMax.value
  return PAD.t + INNER_H - Math.max(0, Math.min(1, ratio)) * INNER_H
}

function xOf(hour: number) {
  return PAD.l + (hour / 23) * INNER_W
}

const yTicks = computed(() => {
  const ticks: Array<{ v: number; y: number; label: string }> = []
  for (let i = 0; i <= 4; i += 1) {
    const v = (scaleMax.value * i) / 4
    ticks.push({ v, y: yOf(v), label: fmt(v) })
  }
  return ticks
})

const referenceLine = computed(() => {
  const ref = props.reference
  if (!ref || !Number.isFinite(ref.value) || ref.value <= 0) return null
  if (ref.value > scaleMax.value) return null
  return { y: yOf(ref.value), label: ref.label }
})

const referenceOutOfRange = computed(() => {
  const ref = props.reference
  return !!ref && Number.isFinite(ref.value) && ref.value > scaleMax.value
})

const nowX = computed(() => {
  const h = props.currentHour
  if (h == null || !Number.isInteger(h) || h < 0 || h > 23) return null
  return round(xOf(h))
})

const nowAnchor = computed(() => {
  const x = nowX.value
  if (x == null) return 'start'
  return x > W - PAD.r - 34 ? 'end' : 'start'
})

/** 把 24 槽位切成连续片段，缺口处断开而非直线连接。 */
function segments(values: (number | null)[]): Pt[][] {
  const out: Pt[][] = []
  let cur: Pt[] = []
  values.forEach((v, i) => {
    if (v == null || !Number.isFinite(v)) {
      if (cur.length) out.push(cur)
      cur = []
      return
    }
    cur.push({ i, v })
  })
  if (cur.length) out.push(cur)
  return out
}

function pathOf(seg: Pt[], close: boolean) {
  if (seg.length < 2) return ''
  const body = seg.map((p, i) => `${i === 0 ? 'M' : 'L'}${round(xOf(p.i))} ${round(yOf(p.v))}`).join(' ')
  if (!close) return body
  const first = seg[0]
  const last = seg[seg.length - 1]
  return `${body} L${round(xOf(last.i))} ${BASELINE} L${round(xOf(first.i))} ${BASELINE} Z`
}

const renderSeries = computed(() =>
  props.series.map((s) => {
    const segs = segments(s.values)
    return {
      key: s.key,
      color: s.color,
      lines: segs.map((seg) => pathOf(seg, false)).filter(Boolean),
      areas: s.kind === 'area' ? segs.map((seg) => pathOf(seg, true)).filter(Boolean) : [],
    }
  }),
)

const ariaLabel = computed(() => {
  const parts = props.series
    .map((s) => {
      const nums = s.values.filter((v): v is number => v != null && Number.isFinite(v))
      if (!nums.length) return `${s.label} 无数据`
      const max = Math.max(...nums)
      const at = s.values.indexOf(max)
      return `${s.label} 峰值 ${fmt(max)}${props.unit ? ` ${props.unit}` : ''} 出现在 ${at}:00`
    })
    .join('；')
  return `${props.title}：${parts || '无数据'}`
})
</script>

<style scoped lang="less">
.hourly-chart {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chart-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.chart-title {
  font-size: var(--fsd-text-sm);
  font-weight: var(--fsd-font-semibold);
  color: var(--fsd-text-primary);
}

.chart-unit {
  font-size: var(--fsd-text-xs);
  color: var(--fsd-text-tertiary);
}

.chart-legend {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-swatch {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  display: inline-block;
}

.legend-text {
  font-size: var(--fsd-text-xs);
  color: var(--fsd-text-secondary);
}

.chart-svg {
  width: 100%;
  height: auto;
  display: block;
}

.grid-line {
  stroke: var(--fsd-border-split);
  stroke-width: 1;
}

.axis-text {
  font-size: 10px;
  fill: var(--fsd-text-tertiary);
  font-family: var(--fsd-font-mono);
}

.ref-line {
  stroke: var(--fsd-border-strong);
  stroke-width: 1;
  stroke-dasharray: 4 4;
}

.ref-text {
  font-size: 10px;
  fill: var(--fsd-text-tertiary);
  font-family: var(--fsd-font-mono);
}

.now-line {
  stroke: var(--fsd-accent);
  stroke-width: 1;
  stroke-dasharray: 2 3;
  opacity: 0.7;
}

.now-text {
  font-size: 10px;
  fill: var(--fsd-accent);
}

.chart-note {
  margin: 0;
  font-size: var(--fsd-text-xs);
  color: var(--fsd-text-tertiary);
}
</style>
