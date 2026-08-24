<template>
  <div v-if="points && points.length > 0" class="trend-chart">
    <div
      v-for="point in points"
      :key="point.label"
      class="trend-bar-row"
      :class="{ clickable }"
      :role="clickable ? 'button' : undefined"
      :tabindex="clickable ? 0 : undefined"
      :aria-label="clickable ? point.label + ': ' + valueLabel(point) : undefined"
      @click="onBarClick(point)"
      @keydown.enter="onBarClick(point)"
      @keydown.space.prevent="onBarClick(point)"
    >
      <span class="trend-label">{{ point.label }}</span>
      <div class="trend-bar-track">
        <div
          class="trend-bar-fill"
          :style="{
            width: `${barWidth(point)}%`,
            background: fillStyle(point),
          }"
          :title="`${point.label}: ${valueLabel(point)}`"
        />
      </div>
      <span class="trend-value">{{ valueLabel(point) }}</span>
    </div>
  </div>
  <div v-else class="trend-empty">
    <span class="trend-empty-text">暂无趋势数据</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AnalyticsTrendPoint } from '@/types/analytics'

const props = withDefaults(
  defineProps<{
    points: AnalyticsTrendPoint[]
    metric?: 'completionRate' | 'totalCount'
    color?: string
    clickable?: boolean
  }>(),
  {
    metric: 'completionRate',
    color: 'var(--fsd-accent)',
    clickable: false,
  },
)

const emit = defineEmits<{
  barClick: [point: AnalyticsTrendPoint]
}>()

const maxValue = computed(() => {
  if (props.metric === 'totalCount') {
    return Math.max(1, ...props.points.map((p) => p.totalCount))
  }
  return 100
})

function barWidth(point: AnalyticsTrendPoint) {
  if (props.metric === 'totalCount') {
    return (point.totalCount / maxValue.value) * 100
  }
  return point.completionRate
}

function fillStyle(point: AnalyticsTrendPoint) {
  if (props.metric === 'completionRate') {
    if (point.completionRate < 60) return 'var(--fsd-error)'
    if (point.completionRate < 85) return 'var(--fsd-warning)'
    return 'var(--fsd-accent)'
  }
  return props.color || 'var(--fsd-accent)'
}

function valueLabel(point: AnalyticsTrendPoint) {
  if (props.metric === 'totalCount') {
    return String(point.totalCount)
  }
  return `${point.completionRate.toFixed(1)}%`
}

function onBarClick(point: AnalyticsTrendPoint) {
  if (props.clickable) {
    emit('barClick', point)
  }
}
</script>

<style scoped lang="less">
.trend-chart {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.trend-empty {
  padding: 24px;
  text-align: center;
  color: var(--fsd-text-tertiary);
  font-size: 12px;
}

.trend-bar-row {
  display: grid;
  grid-template-columns: 44px 1fr 52px;
  gap: 10px;
  align-items: center;

  &.clickable {
    cursor: pointer;
    border-radius: 4px;
    padding: 3px 6px;
    margin: -3px -6px;
    transition: background-color var(--fsd-transition-base);
    &:hover {
      background: var(--fsd-accent-selected);
      .trend-label {
        color: var(--fsd-accent-strong);
      }
    }

    &:focus-visible {
      outline: 2px solid var(--fsd-accent);
      outline-offset: 2px;
    }
  }
}

.trend-label {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
  font-family: var(--fsd-font-mono);
  transition: color var(--fsd-transition-base);
}

.trend-bar-track {
  height: 8px;
  background: var(--fsd-bg-hover);
  border-radius: 999px;
  overflow: hidden;
}

.trend-bar-fill {
  height: 100%;
  border-radius: 999px;
  transition: width var(--fsd-transition-slow);
}

.trend-value {
  font-size: 11px;
  color: var(--fsd-text-secondary);
  text-align: right;
  font-family: var(--fsd-font-mono);
}
</style>
