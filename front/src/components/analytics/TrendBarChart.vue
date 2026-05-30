<template>
  <div class="trend-chart">
    <div v-for="point in points" :key="point.label" class="trend-bar-row">
      <span class="trend-label">{{ point.label }}</span>
      <div class="trend-bar-track">
        <div
          class="trend-bar-fill"
          :style="{ width: `${barWidth(point)}%`, background: color }"
          :title="`${point.label}: ${valueLabel(point)}`"
        />
      </div>
      <span class="trend-value">{{ valueLabel(point) }}</span>
    </div>
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
  }>(),
  {
    metric: 'completionRate',
    color: '#00B4D8',
  }
)

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

function valueLabel(point: AnalyticsTrendPoint) {
  if (props.metric === 'totalCount') {
    return String(point.totalCount)
  }
  return `${point.completionRate.toFixed(1)}%`
}
</script>

<style scoped lang="less">
.trend-chart {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.trend-bar-row {
  display: grid;
  grid-template-columns: 44px 1fr 52px;
  gap: 10px;
  align-items: center;
}

.trend-label {
  font-size: 11px;
  color: var(--fsd-text-tertiary);
  font-family: 'JetBrains Mono', monospace;
}

.trend-bar-track {
  height: 8px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 999px;
  overflow: hidden;
}

.trend-bar-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.3s ease;
}

.trend-value {
  font-size: 11px;
  color: var(--fsd-text-secondary);
  text-align: right;
  font-family: 'JetBrains Mono', monospace;
}
</style>
