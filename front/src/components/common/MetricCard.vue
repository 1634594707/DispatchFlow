<template>
  <component
    :is="clickable ? 'button' : 'div'"
    class="metric-card"
    :class="[`metric-card--${variant}`, `metric-card--${resolvedColor}`, {
      'metric-card--clickable': clickable,
      'metric-card--alert': alert && variant === 'stat',
      'metric-card--loading': loading,
    }]"
    :type="clickable ? 'button' : undefined"
    :disabled="disabled"
    :tabindex="clickable ? 0 : undefined"
    @click="handleClick"
    @keydown.enter="handleClick"
    @keydown.space.prevent="handleClick"
  >
    <template v-if="loading">
      <div class="metric-card-skeleton">
        <div v-if="variant === 'stat'" class="skel-icon" />
        <div class="skel-label" />
        <div class="skel-value" />
        <div v-if="variant === 'stat'" class="skel-action" />
      </div>
    </template>
    <template v-else>
      <div v-if="icon && variant === 'stat'" class="metric-icon" :style="{ background: iconBg, color: iconColor }">
        <component :is="icon" />
      </div>
      <div class="metric-body">
        <div class="metric-label">{{ label }}</div>
        <div class="metric-value-row">
          <span class="metric-value" :style="{ color: valueColor }">
            {{ formattedValue }}
            <small v-if="unit" class="metric-unit">{{ unit }}</small>
          </span>
          <span v-if="trend !== undefined" class="metric-trend" :class="trendDirection">
            <span class="trend-arrow">{{ trendArrow }}</span>
            <span class="trend-delta">{{ formattedTrend }}</span>
          </span>
        </div>
      </div>
      <div v-if="variant === 'stat' && actionText" class="metric-action">
        {{ actionText }}
        <RightOutlined />
      </div>
      <div v-if="variant === 'detail' && $slots.default" class="metric-detail">
        <slot />
      </div>
    </template>
  </component>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RightOutlined } from '@ant-design/icons-vue'
import type { Component } from 'vue'

type ColorTheme = 'cyan' | 'green' | 'amber' | 'red' | 'neutral' | 'info'

const props = withDefaults(
  defineProps<{
    variant?: 'stat' | 'compact' | 'detail'
    label: string
    value?: number | string
    unit?: string
    icon?: Component
    iconBg?: string
    iconColor?: string
    colorTheme?: ColorTheme
    valueColor?: string
    clickable?: boolean
    disabled?: boolean
    alert?: boolean
    actionText?: string
    trend?: number
    trendUnit?: string
    loading?: boolean
    formatValue?: 'number' | 'percent' | 'minutes' | 'currency' | 'raw'
  }>(),
  {
    variant: 'stat',
    formatValue: 'number',
  },
)

const emit = defineEmits<{ click: [] }>()

const colorMap: Record<ColorTheme, { bg: string; color: string }> = {
  cyan:    { bg: 'rgba(34, 199, 230, 0.10)', color: '#22C7E6' },
  green:   { bg: 'rgba(45, 224, 138, 0.10)', color: '#2DE08A' },
  amber:   { bg: 'rgba(255, 192, 77, 0.10)',  color: '#FFC04D' },
  red:     { bg: 'rgba(255, 92, 124, 0.10)',  color: '#FF5C7C' },
  neutral: { bg: 'rgba(155, 168, 184, 0.08)', color: '#9BA8B8' },
  info:    { bg: 'rgba(34, 199, 230, 0.08)',  color: '#22C7E6' },
}

const resolvedColor = computed(() => {
  if (props.valueColor) return 'custom'
  return (props.colorTheme || (props.variant === 'stat' ? 'cyan' : 'neutral'))
})

const computedIconBg = computed(() =>
  props.iconBg || colorMap[props.colorTheme || 'cyan'].bg,
)
const computedIconColor = computed(() =>
  props.iconColor || colorMap[props.colorTheme || 'cyan'].color,
)

const computedValueColor = computed(() => {
  if (props.valueColor) return props.valueColor
  if (props.alert) return colorMap.red.color
  return undefined
})

const formattedValue = computed(() => {
  if (props.value === undefined || props.value === null || props.value === '-') return '-'
  const num = typeof props.value === 'string' ? parseFloat(props.value) : props.value
  if (isNaN(num)) return String(props.value)

  switch (props.formatValue) {
    case 'percent':
      return num.toFixed(1)
    case 'minutes':
      return Math.round(num).toString()
    case 'currency':
      return num.toLocaleString('zh-CN', { style: 'currency', currency: 'CNY', minimumFractionDigits: 0, maximumFractionDigits: 0 })
    case 'raw':
      return String(props.value)
    default:
      return num >= 1000 ? num.toLocaleString('zh-CN') : num.toString()
  }
})

const trendDirection = computed(() => {
  if (props.trend === undefined || props.trend === null) return ''
  return props.trend > 0 ? 'trend-up' : props.trend < 0 ? 'trend-down' : 'trend-flat'
})

const trendArrow = computed(() => {
  if (props.trend === undefined || props.trend === null) return ''
  return props.trend > 0 ? '↑' : props.trend < 0 ? '↓' : '→'
})

const formattedTrend = computed(() => {
  if (props.trend === undefined || props.trend === null) return ''
  const abs = Math.abs(props.trend)
  const suffix = props.trendUnit || ''
  if (props.formatValue === 'percent') return `${abs.toFixed(1)}%`
  if (abs >= 1000) return `${abs.toLocaleString('zh-CN')}${suffix}`
  return `${abs}${suffix}`
})

function handleClick() {
  if (!props.clickable || props.loading) return
  emit('click')
}
</script>

<style scoped lang="less">
// ── Base ──────────────────────────────────────────────
.metric-card {
  background: var(--fsd-bg-base);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-lg);
  padding: 20px;
  font-family: inherit;
  text-align: left;
  color: inherit;
  width: 100%;
  position: relative;
  overflow: hidden;
  transition: border-color 0.25s ease, box-shadow 0.25s ease, transform 0.25s ease;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: var(--fsd-gradient-card);
    pointer-events: none;
  }

  &--clickable {
    cursor: pointer;

    &:hover:not(:disabled) {
      border-color: var(--fsd-border-active);
      transform: translateY(-2px);
      box-shadow: var(--fsd-shadow-elevated);
    }

    &:active:not(:disabled) {
      transform: translateY(0);
      box-shadow: var(--fsd-shadow-elevated), inset 0 2px 4px rgba(0, 0, 0, 0.2);
    }

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    &:focus-visible {
      outline: 2px solid var(--fsd-accent);
      outline-offset: 2px;
    }
  }

  &--alert {
    border-color: var(--fsd-error) !important;
    animation: metric-pulse 2s infinite;
  }

  &--loading {
    pointer-events: none;
  }
}

// ── Variant: stat (Dashboard) ─────────────────────────
.metric-card--stat {
  display: flex;
  flex-direction: column;
  min-height: 150px;
}

.metric-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  margin-bottom: 16px;
  flex-shrink: 0;
}

.metric-card--stat .metric-value {
  font-size: 32px;
  font-weight: 800;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  letter-spacing: -0.04em;
  line-height: 1;
}

.metric-action {
  margin-top: auto;
  padding-top: 14px;
  font-size: 12px;
  color: var(--fsd-text-tertiary);
  display: flex;
  align-items: center;
  gap: 4px;
  transition: color 0.2s;

  .metric-card--clickable:hover & {
    color: var(--fsd-accent);
  }
}

// ── Variant: compact (Analytics) ──────────────────────
.metric-card--compact {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  border-radius: var(--fsd-radius);
}

.metric-card--compact .metric-value {
  font-size: 24px;
  font-weight: 600;
  font-family: 'JetBrains Mono', monospace;
  line-height: 1.2;
}

// ── Variant: detail (SystemHealth) ────────────────────
.metric-card--detail {
  padding: 16px 20px;
}

.metric-card--detail .metric-value {
  font-size: 28px;
  font-weight: 700;
  font-family: 'JetBrains Mono', monospace;
  line-height: 1.2;
  margin-top: 4px;
}

.metric-detail {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 12px;
  border-top: 1px solid var(--fsd-border);
}

// ── Shared ────────────────────────────────────────────
.metric-body {
  position: relative;
  z-index: 0;
}

.metric-label {
  font-size: 13px;
  color: var(--fsd-text-secondary);
  margin-bottom: 6px;
  font-weight: 400;
}

.metric-value-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.metric-unit {
  font-size: 0.5em;
  font-weight: 500;
  opacity: 0.7;
  margin-left: 2px;
}

// ── Trend ─────────────────────────────────────────────
.metric-trend {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 13px;
  font-weight: 600;
  font-family: 'JetBrains Mono', monospace;

  &.trend-up   { color: var(--fsd-success); }
  &.trend-down { color: var(--fsd-error); }
  &.trend-flat { color: var(--fsd-text-tertiary); }
}

.trend-arrow {
  font-size: 14px;
}

// ── Skeleton ──────────────────────────────────────────
.metric-card-skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skel-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: var(--fsd-bg-elevated);
  animation: skel-shimmer 1.4s infinite;
}

.skel-label {
  height: 14px;
  width: 60%;
  border-radius: 4px;
  background: var(--fsd-bg-elevated);
  animation: skel-shimmer 1.4s infinite;
}

.skel-value {
  height: 36px;
  width: 45%;
  border-radius: 6px;
  background: var(--fsd-bg-elevated);
  animation: skel-shimmer 1.4s infinite;
  animation-delay: 0.1s;
}

.skel-action {
  height: 12px;
  width: 35%;
  margin-top: 12px;
  border-radius: 4px;
  background: var(--fsd-bg-elevated);
  animation: skel-shimmer 1.4s infinite;
  animation-delay: 0.2s;
}

// ── Animations ────────────────────────────────────────
@keyframes metric-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(255, 92, 124, 0.4); }
  50%      { box-shadow: 0 0 0 8px rgba(255, 92, 124, 0); }
}

@keyframes skel-shimmer {
  0%, 100% { opacity: 0.4; }
  50%      { opacity: 0.7; }
}
</style>
