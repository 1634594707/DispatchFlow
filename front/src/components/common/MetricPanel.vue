<template>
  <component
    :is="clickable ? 'button' : 'section'"
    class="metric-panel"
    :class="[
      'metric-panel--' + tone,
      { 'metric-panel--actionable': clickable, 'metric-panel--loading': loading },
    ]"
    :type="clickable ? 'button' : undefined"
    :disabled="clickable ? disabled || loading : undefined"
    :aria-label="clickable ? ariaLabel || label : undefined"
    @click="handleActivate"
  >
    <div class="metric-panel-heading">
      <span v-if="icon" class="metric-panel-icon"><component :is="icon" /></span>
      <span class="metric-panel-label">{{ label }}</span>
      <slot name="action" />
    </div>
    <div class="metric-panel-value-row">
      <strong class="metric-panel-value">{{ displayValue }}</strong>
      <small v-if="unit" class="metric-panel-unit">{{ unit }}</small>
    </div>
    <div v-if="$slots.default" class="metric-panel-detail"><slot /></div>
  </component>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import type { MetricTone } from './MetricStrip.vue'

const props = withDefaults(
  defineProps<{
    label: string
    value?: string | number
    unit?: string
    tone?: MetricTone
    icon?: Component
    clickable?: boolean
    disabled?: boolean
    loading?: boolean
    ariaLabel?: string
  }>(),
  {
    value: '-',
    tone: 'neutral',
    clickable: false,
    disabled: false,
    loading: false,
  },
)

const emit = defineEmits<{ activate: [] }>()

const displayValue = computed(() => {
  if (props.loading || props.value === undefined || props.value === null) return '-'
  return typeof props.value === 'number' && props.value >= 1000
    ? props.value.toLocaleString('zh-CN')
    : String(props.value)
})

function handleActivate() {
  if (props.clickable && !props.disabled && !props.loading) emit('activate')
}
</script>

<style scoped lang="less">
.metric-panel {
  display: flex;
  min-width: 0;
  min-height: 132px;
  flex-direction: column;
  gap: var(--fsd-space-4);
  padding: var(--fsd-space-4);
  border: 1px solid var(--fsd-border);
  border-radius: var(--fsd-radius-md);
  background: var(--fsd-surface-workspace);
  color: var(--fsd-text-primary);
  font: inherit;
  text-align: left;
}

.metric-panel--actionable {
  cursor: pointer;
  transition:
    background-color var(--fsd-transition-base),
    border-color var(--fsd-transition-base);

  &:hover:not(:disabled) {
    background: var(--fsd-bg-hover);
    border-color: var(--fsd-border-active);
  }

  &:active:not(:disabled) {
    background: var(--fsd-bg-active);
  }

  &:focus-visible {
    outline: 2px solid var(--fsd-accent-strong);
    outline-offset: 2px;
  }
}

.metric-panel-heading {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--fsd-space-2);
}

.metric-panel-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--fsd-text-secondary);
}

.metric-panel-label {
  overflow: hidden;
  color: var(--fsd-text-secondary);
  font-size: var(--fsd-text-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-panel-value-row {
  display: flex;
  align-items: baseline;
  gap: var(--fsd-space-1);
}

.metric-panel-value {
  color: var(--fsd-text-primary);
  font-family: var(--fsd-font-mono);
  font-size: 32px;
  font-weight: var(--fsd-font-semibold);
  letter-spacing: var(--fsd-tracking-tight);
  line-height: var(--fsd-leading-tight);
}

.metric-panel-unit {
  color: var(--fsd-text-secondary);
  font-size: var(--fsd-text-xs);
}

.metric-panel-detail {
  margin-top: auto;
  padding-top: var(--fsd-space-3);
  border-top: 1px solid var(--fsd-border-split);
}

.metric-panel--accent .metric-panel-value,
.metric-panel--accent .metric-panel-icon {
  color: var(--fsd-accent-strong);
}
.metric-panel--success .metric-panel-value,
.metric-panel--success .metric-panel-icon {
  color: var(--fsd-success);
}
.metric-panel--warning .metric-panel-value,
.metric-panel--warning .metric-panel-icon {
  color: var(--fsd-warning);
}
.metric-panel--error .metric-panel-value,
.metric-panel--error .metric-panel-icon {
  color: var(--fsd-error);
}
.metric-panel--loading {
  opacity: 0.62;
}
</style>
