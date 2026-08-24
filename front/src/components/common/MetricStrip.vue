<template>
  <div
    class="metric-strip"
    :class="{ 'metric-strip--loading': loading }"
    role="list"
    :aria-label="ariaLabel"
  >
    <template v-for="item in items" :key="item.key">
      <button
        v-if="item.actionable && !loading"
        type="button"
        class="metric-strip-item metric-strip-item--actionable"
        :class="'metric-strip-item--' + (item.tone || 'neutral')"
        :disabled="item.disabled"
        :aria-label="item.ariaLabel || item.label"
        role="listitem"
        @click="emit('activate', item)"
      >
        <MetricContent :item="item" />
      </button>
      <div
        v-else
        class="metric-strip-item"
        :class="'metric-strip-item--' + (item.tone || 'neutral')"
        role="listitem"
      >
        <MetricContent :item="item" />
      </div>
    </template>
  </div>
</template>

<script lang="ts">
import { defineComponent, h, type PropType } from 'vue'

export type MetricTone = 'neutral' | 'accent' | 'success' | 'warning' | 'error'

export interface MetricStripItem {
  key: string
  label: string
  value?: number | string
  unit?: string
  tone?: MetricTone
  trend?: number
  trendUnit?: string
  actionable?: boolean
  disabled?: boolean
  ariaLabel?: string
}

const MetricContent = defineComponent({
  name: 'MetricStripContent',
  props: {
    item: {
      type: Object as PropType<MetricStripItem>,
      required: true,
    },
  },
  setup(props) {
    const formatValue = () => {
      const { value } = props.item
      if (value === undefined || value === null || value === '-') return '-'
      return typeof value === 'number' && value >= 1000
        ? value.toLocaleString('zh-CN')
        : String(value)
    }
    const formatTrend = () => {
      const { trend, trendUnit = '' } = props.item
      if (trend === undefined || trend === null) return null
      const arrow = trend > 0 ? '↑' : trend < 0 ? '↓' : '→'
      return [arrow, Math.abs(trend), trendUnit].join('')
    }
    return () => {
      const trend = formatTrend()
      return h('span', { class: 'metric-strip-content' }, [
        h('span', { class: 'metric-strip-label' }, props.item.label),
        h('span', { class: 'metric-strip-value-row' }, [
          h('strong', { class: 'metric-strip-value' }, [
            formatValue(),
            props.item.unit ? h('small', { class: 'metric-strip-unit' }, props.item.unit) : null,
          ]),
          trend
            ? h(
                'span',
                {
                  class: [
                    'metric-strip-trend',
                    props.item.trend && props.item.trend < 0 ? 'metric-strip-trend--down' : '',
                  ],
                },
                trend,
              )
            : null,
        ]),
      ])
    }
  },
})
</script>

<script setup lang="ts">
withDefaults(
  defineProps<{
    items: MetricStripItem[]
    loading?: boolean
    ariaLabel?: string
  }>(),
  {
    loading: false,
    ariaLabel: '关键指标',
  },
)

const emit = defineEmits<{
  activate: [item: MetricStripItem]
}>()
</script>

<style lang="less">
.metric-strip {
  display: grid;
  min-width: 0;
  grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
  border-block: 1px solid var(--fsd-border);
}

.metric-strip-item {
  display: flex;
  min-width: 0;
  min-height: 92px;
  padding: var(--fsd-space-3) var(--fsd-space-4);
  border: 0;
  border-right: 1px solid var(--fsd-border);
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;

  &:last-child {
    border-right: 0;
  }
}

.metric-strip-item--actionable {
  cursor: pointer;
  transition:
    background-color var(--fsd-transition-fast),
    color var(--fsd-transition-fast);

  &:hover:not(:disabled) {
    background: var(--fsd-bg-hover);
  }

  &:active:not(:disabled) {
    background: var(--fsd-bg-active);
  }

  &:focus-visible {
    position: relative;
    z-index: 1;
    outline: 2px solid var(--fsd-accent-strong);
    outline-offset: -2px;
  }

  &:disabled {
    cursor: not-allowed;
    color: var(--fsd-text-muted);
  }
}

.metric-strip-content {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  gap: var(--fsd-space-1);
}

.metric-strip-label {
  overflow: hidden;
  color: var(--fsd-text-secondary);
  font-size: var(--fsd-text-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-strip-value-row {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: var(--fsd-space-2);
}

.metric-strip-value {
  overflow: hidden;
  color: var(--fsd-text-primary);
  font-family: var(--fsd-font-mono);
  font-size: var(--fsd-text-2xl);
  font-weight: var(--fsd-font-semibold);
  letter-spacing: var(--fsd-tracking-tight);
  line-height: var(--fsd-leading-tight);
  text-overflow: ellipsis;
}

.metric-strip-unit {
  margin-left: 2px;
  color: var(--fsd-text-secondary);
  font-family: var(--fsd-font-sans);
  font-size: var(--fsd-text-xs);
  font-weight: var(--fsd-font-medium);
}

.metric-strip-trend {
  color: var(--fsd-success);
  font-family: var(--fsd-font-mono);
  font-size: var(--fsd-text-xs);
  white-space: nowrap;
}

.metric-strip-trend--down {
  color: var(--fsd-error);
}

.metric-strip-item--accent .metric-strip-value {
  color: var(--fsd-accent-strong);
}
.metric-strip-item--success .metric-strip-value {
  color: var(--fsd-success);
}
.metric-strip-item--warning .metric-strip-value {
  color: var(--fsd-warning);
}
.metric-strip-item--error .metric-strip-value {
  color: var(--fsd-error);
}

.metric-strip--loading {
  opacity: 0.62;
}

@media (max-width: 767px) {
  .metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-strip-item:nth-child(2n) {
    border-right: 0;
  }

  .metric-strip-item {
    min-height: 84px;
  }
}
</style>
